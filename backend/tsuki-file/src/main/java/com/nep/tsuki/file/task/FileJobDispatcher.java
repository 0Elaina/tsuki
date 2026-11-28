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

@Slf4j
@Component
@RequiredArgsConstructor
public class FileJobDispatcher {
    private final FileJobMapper fileJobMapper;
    private final TransactionTemplate transactionTemplate;
    private final RabbitTemplate rabbitTemplate;

    private record PendingConfirm(Long jobId, FileJobType jobType, CorrelationData correlationData) {
    }

    /**
     * 定时轮询派发待处理任务：每轮先派一批交互任务，再派一批清理任务，有积压时公平轮转，排空后延迟 1s 再扫描
     */
    @Scheduled(fixedDelay = 1000L)
    public void dispatchPendingJobs() {
        boolean interactiveActive = !FileJobType.INTERACTIVE_TYPE_NAMES.isEmpty();
        boolean cleanupActive = !FileJobType.CLEANUP_TYPE_NAMES.isEmpty();

        while (interactiveActive || cleanupActive) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            if (interactiveActive) {
                interactiveActive = dispatchSingleBatch(FileJobType.INTERACTIVE_TYPE_NAMES);
            }
            if (cleanupActive) {
                cleanupActive = dispatchSingleBatch(FileJobType.CLEANUP_TYPE_NAMES);
            }
        }
    }

    /**
     * 派发单个队列分组的一批文件任务到 RabbitMQ
     *
     * @return true 表示本批次满载且正常投递，可继续轮转；false 表示无积压、未满载或发生异常/超时
     */
    private boolean dispatchSingleBatch(List<String> supportedTypes) {
        if (supportedTypes.isEmpty()) {
            return false;
        }

        String batchToken = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime claimUntil = now.plusSeconds(FileJobConstants.DISPATCH_CLAIM_DELAY_SECONDS);

        List<FileJob> claimedJobs = transactionTemplate.execute(status -> fileJobMapper.claimDispatchBatch(
                supportedTypes,
                now,
                claimUntil,
                batchToken,
                FileJobConstants.DISPATCH_BATCH_SIZE));
        if (claimedJobs == null || claimedJobs.isEmpty()) {
            return false;
        }

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
            return false;
        }

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
                return false;
            }
        }

        if (ackedCount < claimedJobs.size()) {
            log.warn("文件任务批次存在未确认或退回消息, batchToken={}, claimed={}, sent={}, acked={}",
                    batchToken, claimedJobs.size(), pendingConfirms.size(), ackedCount);
        }

        return !sendFailed && !confirmTimeout && claimedJobs.size() == FileJobConstants.DISPATCH_BATCH_SIZE;
    }
}
