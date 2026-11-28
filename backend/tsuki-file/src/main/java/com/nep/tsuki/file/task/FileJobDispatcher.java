package com.nep.tsuki.file.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.nep.tsuki.file.constants.FileJobConstants;
import com.nep.tsuki.file.constants.FileRabbitConstants;
import com.nep.tsuki.file.mapper.FileJobMapper;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.enums.FileJobType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件任务 Outbox 派发器，按交互/清理双队列公平轮转批量投递到 RabbitMQ，并在 Broker 确认后按任务类型设置补派时间
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileJobDispatcher {
    private final FileJobMapper fileJobMapper;
    private final TransactionTemplate transactionTemplate;
    private final RabbitTemplate rabbitTemplate;

    private record PendingConfirm(Long jobId, FileJobType jobType, CorrelationData correlationData) {
    }

    private enum BatchOutcome {
        /** 本批满载且正常确认，可能仍有积压 */
        HAS_MORE,
        /** 当前队列已无更多到期任务 */
        DRAINED,
        /** Broker 发送/确认或数据库异常，本轮应立即休眠退避 */
        FAILED
    }

    /**
     * 定时轮询派发待处理任务：每轮优先检查交互队列再检查清理队列，最多轮转固定轮数，排空或异常后休眠 1 秒再扫描
     */
    @Scheduled(fixedDelay = 1000L)
    public void dispatchPendingJobs() {
        for (int round = 0; round < FileJobConstants.MAX_DISPATCH_ROUNDS_PER_RUN; round++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            // 每轮均重新检查交互队列，防止清理队列持续积压期间新确认的上传或衍生物请求被饿死
            BatchOutcome interactiveOutcome = dispatchSingleBatch(FileJobType.INTERACTIVE_TYPE_NAMES);
            if (interactiveOutcome == BatchOutcome.FAILED) {
                break;
            }

            BatchOutcome cleanupOutcome = dispatchSingleBatch(FileJobType.CLEANUP_TYPE_NAMES);
            if (cleanupOutcome == BatchOutcome.FAILED) {
                break;
            }

            if (interactiveOutcome == BatchOutcome.DRAINED && cleanupOutcome == BatchOutcome.DRAINED) {
                break;
            }
        }
    }

    /**
     * 派发单个队列分组的一批到期任务到 RabbitMQ，并按 Publisher Confirm 结果回写按类型区分的补派时间
     *
     * @param supportedTypes 本批次领取的任务类型名称集合
     * @return 本批次派发结果（有积压、已排空或发生故障）
     */
    private BatchOutcome dispatchSingleBatch(List<String> supportedTypes) {
        if (supportedTypes.isEmpty()) {
            return BatchOutcome.DRAINED;
        }

        // 1. 短事务跳过锁批量抢占到期任务并写入 15s 派发租约与批次令牌
        String batchToken = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime claimUntil = now.plusSeconds(FileJobConstants.DISPATCH_CLAIM_DELAY_SECONDS);

        List<FileJob> claimedJobs;
        try {
            claimedJobs = transactionTemplate.execute(status -> fileJobMapper.claimDispatchBatch(
                    supportedTypes,
                    now,
                    claimUntil,
                    batchToken,
                    FileJobConstants.DISPATCH_BATCH_SIZE));
        } catch (DataAccessException ex) {
            log.error("抢占待派发文件任务批次失败, batchToken={}", batchToken, ex);
            return BatchOutcome.FAILED;
        }
        if (claimedJobs == null || claimedJobs.isEmpty()) {
            return BatchOutcome.DRAINED;
        }

        // 2. 在单条信道上下文内连续投递持久化消息，携带 CorrelationData 追踪确认与退回
        List<PendingConfirm> pendingConfirms = new ArrayList<>(claimedJobs.size());
        boolean sendFailed = false;
        try {
            rabbitTemplate.invoke(operations -> {
                for (FileJob job : claimedJobs) {
                    FileJobType jobType = FileJobType
                            .findByName(job.getType())
                            .orElseThrow(() -> new IllegalStateException("未知文件任务类型: " + job.getType()));
                    String jobIdText = String.valueOf(job.getId());
                    CorrelationData correlationData = new CorrelationData(batchToken + ":" + jobIdText);

                    operations.convertAndSend(
                            FileRabbitConstants.JOBS_EXCHANGE,
                            jobType.getRoutingKey(),
                            jobIdText,
                            message -> {
                                MessageProperties properties = message.getMessageProperties();
                                properties.setMessageId(jobIdText);
                                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                                return message;
                            },
                            correlationData);

                    pendingConfirms.add(new PendingConfirm(job.getId(), jobType, correlationData));
                }
                return null;
            });
        } catch (AmqpException ex) {
            sendFailed = true;
            log.warn("批量派发文件任务到 RabbitMQ 失败, batchToken={}, sentCount={}", batchToken, pendingConfirms.size(), ex);
        }

        if (pendingConfirms.isEmpty()) {
            return BatchOutcome.FAILED;
        }

        // 3. 等待本批次 Publisher Confirm，超时后仅回写已确认且未被 Return 的记录
        CompletableFuture<?>[] futures = pendingConfirms.stream()
                .map(pc -> pc.correlationData().getFuture())
                .toArray(CompletableFuture[]::new);
        boolean confirmTimeout = false;
        try {
            CompletableFuture.allOf(futures).get(FileJobConstants.DISPATCH_CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            confirmTimeout = true;
        } catch (TimeoutException | ExecutionException ex) {
            confirmTimeout = true;
            log.warn("批量文件任务派发确认超时, batchToken={}, sentCount={}", batchToken, pendingConfirms.size(), ex);
        }

        Map<FileJobType, List<Long>> ackedIdsByType = new EnumMap<>(FileJobType.class);
        int ackedCount = 0;
        for (PendingConfirm pc : pendingConfirms) {
            CompletableFuture<CorrelationData.Confirm> future = pc.correlationData().getFuture();
            if (future.isDone() && !future.isCompletedExceptionally()) {
                CorrelationData.Confirm confirm = future.getNow(null);
                if (confirm != null && confirm.ack() && pc.correlationData().getReturned() == null) {
                    ackedIdsByType.computeIfAbsent(pc.jobType(), key -> new ArrayList<>()).add(pc.jobId());
                    ackedCount++;
                }
            }
        }

        // 4. 从准备回写该批确认的时刻起，按任务类型写入各自的补派时间（+30s / +60s / +5min）
        if (!ackedIdsByType.isEmpty()) {
            OffsetDateTime dispatchedAt = OffsetDateTime.now(ZoneOffset.UTC);
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    for (Map.Entry<FileJobType, List<Long>> entry : ackedIdsByType.entrySet()) {
                        OffsetDateTime redeliverAfter = dispatchedAt.plus(entry.getKey().getRedeliveryDelay());
                        fileJobMapper.markDispatchedBatch(entry.getValue(), batchToken, dispatchedAt, redeliverAfter);
                    }
                });
            } catch (DataAccessException ex) {
                log.error("回写文件任务投递确认状态失败, batchToken={}, ackedCount={}", batchToken, ackedCount, ex);
                return BatchOutcome.FAILED;
            }
        }

        if (ackedCount < claimedJobs.size()) {
            log.warn("文件任务批次存在未确认或退回消息, batchToken={}, claimed={}, sent={}, acked={}",
                    batchToken, claimedJobs.size(), pendingConfirms.size(), ackedCount);
        }

        if (sendFailed || confirmTimeout) {
            return BatchOutcome.FAILED;
        }
        return claimedJobs.size() == FileJobConstants.DISPATCH_BATCH_SIZE
                ? BatchOutcome.HAS_MORE
                : BatchOutcome.DRAINED;
    }
}
