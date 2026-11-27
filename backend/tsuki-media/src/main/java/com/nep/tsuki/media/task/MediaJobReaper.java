package com.nep.tsuki.media.task;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nep.tsuki.media.config.MediaJobsProperties;
import com.nep.tsuki.media.constants.MediaJobConstants;
import com.nep.tsuki.media.mapper.MediaAssetMapper;
import com.nep.tsuki.media.mapper.MediaJobMapper;
import com.nep.tsuki.media.model.dto.ExpiredMediaJob;
import com.nep.tsuki.media.model.entity.MediaAsset;
import com.nep.tsuki.media.model.enums.MediaAssetStatus;
import com.nep.tsuki.media.model.enums.MediaJobFailureCode;
import com.nep.tsuki.media.model.enums.MediaJobStatus;
import com.nep.tsuki.media.model.enums.MediaJobType;
import com.nep.tsuki.media.storage.MediaObjectStorage;
import com.nep.tsuki.media.utils.MediaObjectPathUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaJobReaper implements SchedulingConfigurer {
    private final MediaJobMapper mediaJobMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final MediaObjectStorage mediaObjectStorage;
    private final TransactionTemplate transactionTemplate;
    private final MediaJobsProperties mediaJobsProperties;

    /**
     * 单个被回收处理的超时任务结果快照
     */
    private record ReclaimedJob(Long id, Long assetId, String type, int attemptCount, boolean requeued) {
    }

    /**
     * 单批次超时任务回收的执行结果汇总
     */
    private record ReapBatchOutcome(List<ReclaimedJob> reclaimedJobs, boolean hasMore) {
    }

    /**
     * 注册后台定时调度任务：以固定延迟（Fixed Delay）周期性触发过期媒体任务的回收扫描
     *
     * @param taskRegistrar Spring 定时任务注册器
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(this::reapExpiredJobs, mediaJobsProperties.getReapInterval());
    }

    /**
     * 定时任务主入口：循环分批扫描并回收租约已过期的运行中媒体任务
     */
    public void reapExpiredJobs() {
        for (int batch = 0; batch < MediaJobConstants.MAX_REAP_BATCHES_PER_RUN; batch++) {
            // 响应线程池关闭或中断信号，优雅退出当前轮次的剩余批次扫描
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            // 执行单批回收；若返回 false 说明当前已无更多过期任务，提前结束本轮扫描
            if (!reapSingleBatch()) {
                break;
            }
        }
    }

    /**
     * 扫描并回收单批次租约过期的媒体任务：事务内分流重排重试或标记终态失败，事务外清理对象存储残留文件
     *
     * @return {@code true} 表示本批已达处理上限，队列可能仍有积压任务需继续扫描；否则返回 {@code false}
     */
    private boolean reapSingleBatch() {
        // 在事务内批量扫描并分类处理已过期的运行中任务
        ReapBatchOutcome outcome = transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            // 加行锁查询一批已超时且仍处于运行状态的任务
            List<ExpiredMediaJob> expiredJobs = mediaJobMapper.selectExpiredRunningForUpdate(now,
                    MediaJobConstants.REAP_BATCH_SIZE);
            if (expiredJobs.isEmpty()) {
                return new ReapBatchOutcome(List.of(), false);
            }

            // 建立任务 ID 到任务详情的映射，方便后续数据库批量更新后快速组装返回结果
            Map<Long, ExpiredMediaJob> jobById = new LinkedHashMap<>(expiredJobs.size());
            // 按当前已尝试次数分组待重试的任务（同组共享相同的退避延迟策略）
            Map<Integer, List<Long>> retryJobIdsByAttempt = new LinkedHashMap<>();
            // 收集重试次数已耗尽、需直接标记为最终失败的任务 ID
            List<Long> terminalJobIds = new ArrayList<>();

            // 遍历超时任务：建立索引并根据剩余重试额度分流
            for (ExpiredMediaJob job : expiredJobs) {
                jobById.put(job.id(), job);
                // 判断当前尝试次数是否还有对应的重试延迟配置（即未达到最大重试上限）
                if (MediaJobConstants.retryDelayAfterAttempt(job.attemptCount()).isPresent()) {
                    // 按重试次数分组收集任务ID，便于后续按相同退避时间批量处理
                    retryJobIdsByAttempt.computeIfAbsent(job.attemptCount(), key -> new ArrayList<>()).add(job.id());
                } else {
                    // 重试次数已耗尽，归入终态失败集合
                    terminalJobIds.add(job.id());
                }
            }

            // 初始化本批实际回收结果列表（供事务提交后清理对象存储残留文件等下游逻辑使用）
            List<ReclaimedJob> reclaimedJobs = new ArrayList<>(expiredJobs.size());

            // 按尝试次数分组批量重排为 PENDING
            for (Map.Entry<Integer, List<Long>> entry : retryJobIdsByAttempt.entrySet()) {
                int attemptCount = entry.getKey();
                // 计算下一次可被调度执行的时间（当前时间 + 对应退避延迟）
                Duration retryDelay = MediaJobConstants.retryDelayAfterAttempt(attemptCount).orElseThrow();
                OffsetDateTime nextAvailableAt = now.plus(retryDelay);
                // 批量更新状态为待重试，记录租约过期失败原因，并返回实际更新成功的任务 ID
                List<Long> updateIds = mediaJobMapper.rescheduleExpiredBatchForRetry(
                        entry.getValue(),
                        attemptCount,
                        nextAvailableAt,
                        MediaJobFailureCode.LEASE_EXPIRED.name(),
                        now);

                // 记录实际重排成功的任务（requeued = true）
                for (Long updateId : updateIds) {
                    ExpiredMediaJob job = jobById.get(updateId);
                    reclaimedJobs.add(new ReclaimedJob(
                            job.id(),
                            job.assetId(),
                            job.type(),
                            job.attemptCount(),
                            true));
                }
            }

            // 批量标记终态 FAILED 并同步将对应资产置为 PROCESSING_FAILED
            if (!terminalJobIds.isEmpty()) {
                // 批量将任务置为终态失败，并返回实际更新成功的任务 ID
                List<Long> failureIds = mediaJobMapper.markExpiredBatchFailed(
                        terminalJobIds,
                        MediaJobFailureCode.LEASE_EXPIRED.name(),
                        now);

                Set<Long> failedAssetIds = new LinkedHashSet<>(failureIds.size());
                for (Long failureId : failureIds) {
                    ExpiredMediaJob job = jobById.get(failureId);
                    // 记录终态失败的任务（requeued = false）
                    reclaimedJobs.add(new ReclaimedJob(
                            job.id(),
                            job.assetId(),
                            job.type(),
                            job.attemptCount(),
                            false));
                    // 仅当该任务类型负责推进资产主状态时，才收集资产 ID 用于同步失败状态
                    if (MediaJobType.findByName(job.type()).map(MediaJobType::isAdvancesAssetStatus).orElse(false)) {
                        failedAssetIds.add(job.assetId());
                    }
                }
                // 乐观校验资产仍处于 UPLOAD_CONFIRMED 状态后，批量流转为 PROCESSING_FAILED
                if (!failedAssetIds.isEmpty()) {
                    mediaAssetMapper.update(null, new LambdaUpdateWrapper<MediaAsset>()
                            .in(MediaAsset::getId, failedAssetIds)
                            .eq(MediaAsset::getStatus, MediaAssetStatus.UPLOAD_CONFIRMED.name())
                            .set(MediaAsset::getStatus, MediaAssetStatus.PROCESSING_FAILED.name())
                            .set(MediaAsset::getUpdatedAt, now));
                }
            }

            // 若本批查满上限，说明队列中可能还有更多超时任务，通知外层继续循环扫描
            boolean hasMore = expiredJobs.size() == MediaJobConstants.REAP_BATCH_SIZE;
            return new ReapBatchOutcome(reclaimedJobs, hasMore);
        });

        // 防御性判空：事务执行异常返回 null 时，终止外层循环扫描
        if (outcome == null) {
            return false;
        }

        // 事务成功提交后，在事务外执行日志审计与对象存储残留文件清理
        if (!outcome.reclaimedJobs().isEmpty()) {
            // 收集本次超时尝试周期内可能已写入对象存储的临时/中间产物路径
            List<String> keysToDelete = new ArrayList<>();
            for (ReclaimedJob reclaimed : outcome.reclaimedJobs()) {
                // 根据是否重新入队确定任务流转后的目标状态，用于监控告警日志记录
                String targetStatus = reclaimed.requeued()
                        ? MediaJobStatus.PENDING.name()
                        : MediaJobStatus.FAILED.name();
                log.warn("回收租期超时媒体任务: jobId={}, assetId={}, type={}, attemptCount={}, targetStatus={}", reclaimed.id(),
                        reclaimed.assetId(), reclaimed.type(), reclaimed.attemptCount(), targetStatus);
                // 解析任务类型并推导出该次尝试对应的候选对象存储 Key，防止残留脏数据污染后续重试
                MediaJobType.findByName(reclaimed.type())
                        .ifPresent(jobType -> keysToDelete.addAll(MediaObjectPathUtils.attemptCandidateKeys(
                                jobType,
                                reclaimed.assetId(),
                                reclaimed.id(),
                                reclaimed.attemptCount())));
            }
            // 静默批量删除对象存储中的残留文件
            if (!keysToDelete.isEmpty()) {
                mediaObjectStorage.deleteQuietly(keysToDelete);
            }
        }
        // 返回是否仍存在待回收的积压任务，供外层调度器决定是否立即触发下一轮批次扫描
        return outcome.hasMore();
    }

}
