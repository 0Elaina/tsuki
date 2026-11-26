package com.nep.tsuki.media.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

import com.nep.tsuki.media.constants.MediaJobConstants;
import com.nep.tsuki.media.constants.MediaRabbitConstants;
import com.nep.tsuki.media.mapper.MediaJobMapper;
import com.nep.tsuki.media.model.dto.DispatchedMediaJob;
import com.nep.tsuki.media.model.enums.MediaJobType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaJobDispatcher {
    private final MediaJobMapper mediaJobMapper;
    private final TransactionTemplate transactionTemplate;
    private final RabbitTemplate rabbitTemplate;

    private static final List<String> SUPPORTED_TYPES = MediaJobType.ALL_TYPE_NAMES;

    private record PendingConfirm(Long jobId, CorrelationData correlationData) {
    }

    /**
     * 定时轮询派发待处理任务
     *
     * 
     * 采用 {@code fixedDelay} 保证上一次排空积压后再开启下一轮计时，避免任务积压时线程重叠执行；
     * 内部通过 while 循环持续拉取批次，直至积压清空或发生异常/超时才进入休眠
     */
    @Scheduled(fixedDelay = 1000L)
    public void dispatchPendingJobs() {
        while (dispatchSingleBatch()) {
        }
    }

    /**
     * 派发单个批次的媒体任务到 RabbitMQ（实现 Outbox 模式的可靠投递）
     *
     * @return true 表示本批次满载且全部正常投递，调用方可立即继续派发下一批；
     *         false 表示无待处理任务、未满载或发生异常/超时，调用方应休眠等待下一轮调度
     */
    private boolean dispatchSingleBatch() {
        // 前置校验：当前节点未配置任何支持的任务类型时直接退出
        if (SUPPORTED_TYPES.isEmpty()) {
            return false;
        }

        // 数据库层抢占认领（租约锁机制）
        // 生成本批次唯一令牌，用于后续 CAS 校验，防止租约过期后被其他节点抢占导致的并发覆盖
        String batchToken = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // 计算租约过期时间：若当前节点在发送期间宕机，超过此时间后任务可被其他节点重新认领
        OffsetDateTime claimUntil = now.plusSeconds(MediaJobConstants.DISPATCH_CLAIM_DELAY_SECONDS);

        // 短事务认领一批待派发任务（更新下次调度时间与 batchToken），事务结束立即释放数据库连接
        List<DispatchedMediaJob> claimedJobs = transactionTemplate.execute(status -> mediaJobMapper.claimDispatchBatch(
                SUPPORTED_TYPES,
                now,
                claimUntil,
                batchToken,
                MediaJobConstants.DISPATCH_BATCH_SIZE));
        if (claimedJobs == null || claimedJobs.isEmpty()) {
            return false;
        }

        // 批量发送消息到 RabbitMQ（网络 IO 操作，严禁包裹在数据库事务内）
        List<PendingConfirm> pendingConfirms = new ArrayList<>(claimedJobs.size());
        boolean sendFailed = false;
        try {
            // 使用 invoke 绑定独占同一个 AMQP Channel 连续发送，减少连接池借还开销并提升吞吐量
            rabbitTemplate.invoke(operations -> {
                for (DispatchedMediaJob job : claimedJobs) {
                    // 解析任务类型以获取对应的 RoutingKey
                    MediaJobType jobType = MediaJobType
                            .findByName(job.type())
                            .orElseThrow(() -> new IllegalStateException("未知媒体任务类型: " + job.type()));
                    String jobIdText = String.valueOf(job.id());
                    // 构造发布确认关联凭证，内部包含接收 Broker 异步回执的 CompletableFuture
                    CorrelationData correlationData = new CorrelationData(batchToken + ":" + jobIdText);

                    // 投递瘦消息（Payload 仅携带任务 ID），并设置持久化与消息 ID
                    operations.convertAndSend(
                            MediaRabbitConstants.JOBS_EXCHANGE,
                            jobType.getRoutingKey(),
                            jobIdText,
                            message -> {
                                MessageProperties properties = message.getMessageProperties();
                                properties.setMessageId(jobIdText); // 供下游消费者做幂等去重
                                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT); // 消息持久化，防 Broker 重启丢失
                                return message;
                            }, correlationData);

                    // 仅在本地成功写入发送缓冲区后才加入待确认列表
                    pendingConfirms.add(new PendingConfirm(job.id(), correlationData));
                }
                return null;
            });
        } catch (AmqpException ex) {
            // 捕获发送中途的网络/Broker 异常但不向外抛出，确保异常前已发出的部分消息仍能走后续确认与落库流程
            sendFailed = true;
            log.warn("批量派发媒体任务到 RabbitMQ 失败, batchToken={}, sentCount={}", batchToken, pendingConfirms.size(), ex);
        }

        // 若一条消息都未成功发出（如第一条即断网），直接结束本轮，等待租约过期后重试
        if (pendingConfirms.isEmpty()) {
            return false;
        }

        // 阻塞等待 RabbitMQ Broker 的 Publisher Confirm（发布确认回执）
        CompletableFuture<?>[] futures = pendingConfirms
                .stream()
                .map(pc -> pc.correlationData().getFuture())
                .toArray(CompletableFuture[]::new);
        boolean confirmTimeout = false;
        try {
            // 聚合等待本批所有已发消息的确认结果，设置整体超时上限防止线程无限挂起
            CompletableFuture.allOf(futures).get(MediaJobConstants.DISPATCH_CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // 恢复线程中断标志位
            confirmTimeout = true;
        } catch (TimeoutException | ExecutionException ex) {
            // 超时或部分 Future 异常结束：不中断流程，后续逐个检查哪些消息实际已拿到 ACK
            confirmTimeout = true;
            log.warn("批量媒体任务派发确认超时, batchToken={}, sentCount={}", batchToken, pendingConfirms.size(), ex);
        }

        // 筛选真正投递成功的任务
        List<Long> ackedJobIds = new ArrayList<>(pendingConfirms.size());
        for (PendingConfirm pc : pendingConfirms) {
            CompletableFuture<CorrelationData.Confirm> future = pc.correlationData().getFuture();
            // 正常完成并排除异常结束的情况
            if (future.isDone() && !future.isCompletedExceptionally()) {
                CorrelationData.Confirm confirm = future.getNow(null);
                // 必须同时满足：
                // confirm.ack() == true：消息成功到达 Exchange 并完成持久化
                // getReturned() == null：消息未被 ReturnCallback 退回
                if (confirm != null && confirm.ack() && pc.correlationData().getReturned() == null) {
                    ackedJobIds.add(pc.jobId());
                }
            }
        }

        // 批量回写数据库状态为“已派发”
        if (!ackedJobIds.isEmpty()) {
            OffsetDateTime dispatchedAt = OffsetDateTime.now(ZoneOffset.UTC);
            try {
                // 携带 batchToken 作为乐观锁条件：仅当该任务仍属于当前批次租约时才更新状态，防止并发覆盖
                transactionTemplate.executeWithoutResult(
                        status -> mediaJobMapper.markDispatchedBatch(ackedJobIds,
                                batchToken,
                                dispatchedAt));
            } catch (DataAccessException ex) {
                // 数据库回写失败：已发到 MQ 的消息最终会因租约过期被重复投递，依赖下游消费者幂等性兜底
                log.error("回写媒体任务投递状态失败, batchToken={}, ackedCount={}", batchToken, ackedJobIds.size(), ex);
                return false;
            }
        }

        // 部分失败告警：未 ACK 或被退回的任务不主动重置，依赖第 2 步设定的 claimUntil 租约过期后自动补偿重试
        if (ackedJobIds.size() < claimedJobs.size()) {
            log.warn("媒体任务批次存在未确认或退回消息, batchToken={}, claimed={}, sent={}, acked={}", batchToken, claimedJobs.size(),
                    pendingConfirms.size(), ackedJobIds.size());
        }

        // 返回调度器控制信号：
        // 仅当发送无报错、确认未超时、且本批捞取数量达到上限时返回 true，通知外层循环继续全速派发
        return !sendFailed && !confirmTimeout && claimedJobs.size() == MediaJobConstants.DISPATCH_BATCH_SIZE;
    }
}
