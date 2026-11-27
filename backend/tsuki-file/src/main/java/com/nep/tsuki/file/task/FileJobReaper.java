package com.nep.tsuki.file.task;

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
import com.nep.tsuki.file.config.FileJobsProperties;
import com.nep.tsuki.file.constants.FileJobConstants;
import com.nep.tsuki.file.mapper.FileAssetMapper;
import com.nep.tsuki.file.mapper.FileJobMapper;
import com.nep.tsuki.file.model.entity.FileAsset;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.enums.FileAssetStatus;
import com.nep.tsuki.file.model.enums.FileJobStatus;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.processing.FileJobCode;
import com.nep.tsuki.file.storage.FileObjectStorage;
import com.nep.tsuki.file.utils.FileObjectPathUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileJobReaper implements SchedulingConfigurer {
    private final FileJobMapper fileJobMapper;
    private final FileAssetMapper fileAssetMapper;
    private final FileObjectStorage fileObjectStorage;
    private final TransactionTemplate transactionTemplate;
    private final FileJobsProperties fileJobsProperties;

    private record ReclaimedJob(Long id, Long assetId, String type, int attemptCount, boolean requeued) {
    }

    private record ReapBatchOutcome(List<ReclaimedJob> reclaimedJobs, boolean hasMore) {
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(this::reapExpiredJobs, fileJobsProperties.getReapInterval());
    }

    /**
     * 定时任务主入口：循环分批扫描并回收租约已过期的运行中文件任务
     */
    public void reapExpiredJobs() {
        for (int batch = 0; batch < FileJobConstants.MAX_REAP_BATCHES_PER_RUN; batch++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            if (!reapSingleBatch()) {
                break;
            }
        }
    }

    /**
     * 扫描并回收单批次租约过期的文件任务：事务内分流重排重试或标记终态失败，事务外清理对象存储残留文件
     *
     * @return {@code true} 表示本批已达处理上限，队列可能仍有积压任务需继续扫描；否则返回 {@code false}
     */
    private boolean reapSingleBatch() {
        ReapBatchOutcome outcome = transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            List<FileJob> expiredJobs = fileJobMapper.selectExpiredRunningForUpdate(now,
                    FileJobConstants.REAP_BATCH_SIZE);
            if (expiredJobs.isEmpty()) {
                return new ReapBatchOutcome(List.of(), false);
            }

            Map<Long, FileJob> jobById = new LinkedHashMap<>(expiredJobs.size());
            Map<Integer, List<Long>> retryJobIdsByAttempt = new LinkedHashMap<>();
            List<Long> terminalJobIds = new ArrayList<>();

            for (FileJob job : expiredJobs) {
                jobById.put(job.getId(), job);
                if (FileJobConstants.retryDelayAfterAttempt(job.getAttemptCount()).isPresent()) {
                    retryJobIdsByAttempt.computeIfAbsent(job.getAttemptCount(), key -> new ArrayList<>())
                            .add(job.getId());
                } else {
                    terminalJobIds.add(job.getId());
                }
            }

            List<ReclaimedJob> reclaimedJobs = new ArrayList<>(expiredJobs.size());

            for (Map.Entry<Integer, List<Long>> entry : retryJobIdsByAttempt.entrySet()) {
                int attemptCount = entry.getKey();
                Duration retryDelay = FileJobConstants.retryDelayAfterAttempt(attemptCount).orElseThrow();
                OffsetDateTime nextAvailableAt = now.plus(retryDelay);
                List<Long> updateIds = fileJobMapper.rescheduleExpiredBatchForRetry(
                        entry.getValue(),
                        attemptCount,
                        nextAvailableAt,
                        FileJobCode.LEASE_EXPIRED.code(),
                        "任务执行租期已超时，已重排等待重试",
                        now);

                for (Long updateId : updateIds) {
                    FileJob job = jobById.get(updateId);
                    reclaimedJobs.add(new ReclaimedJob(
                            job.getId(),
                            job.getAssetId(),
                            job.getType(),
                            job.getAttemptCount(),
                            true));
                }
            }

            if (!terminalJobIds.isEmpty()) {
                List<Long> failureIds = fileJobMapper.markExpiredBatchFailed(
                        terminalJobIds,
                        FileJobCode.LEASE_EXPIRED.code(),
                        "任务执行租期已超时，达到最大尝试次数",
                        now);

                Set<Long> failedAssetIds = new LinkedHashSet<>(failureIds.size());
                for (Long failureId : failureIds) {
                    FileJob job = jobById.get(failureId);
                    reclaimedJobs.add(new ReclaimedJob(
                            job.getId(),
                            job.getAssetId(),
                            job.getType(),
                            job.getAttemptCount(),
                            false));
                    if (FileJobType.findByName(job.getType()).map(FileJobType::isAdvancesAssetStatus).orElse(false)) {
                        failedAssetIds.add(job.getAssetId());
                    }
                }
                if (!failedAssetIds.isEmpty()) {
                    fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                            .in(FileAsset::getId, failedAssetIds)
                            .eq(FileAsset::getStatus, FileAssetStatus.UPLOAD_CONFIRMED.name())
                            .set(FileAsset::getStatus, FileAssetStatus.PROCESSING_FAILED.name())
                            .set(FileAsset::getUpdatedAt, now));
                }
            }

            boolean hasMore = expiredJobs.size() == FileJobConstants.REAP_BATCH_SIZE;
            return new ReapBatchOutcome(reclaimedJobs, hasMore);
        });

        if (outcome == null) {
            return false;
        }

        if (!outcome.reclaimedJobs().isEmpty()) {
            List<String> keysToDelete = new ArrayList<>();
            for (ReclaimedJob reclaimed : outcome.reclaimedJobs()) {
                String targetStatus = reclaimed.requeued()
                        ? FileJobStatus.PENDING.name()
                        : FileJobStatus.FAILED.name();
                log.warn("回收租期超时文件任务: jobId={}, assetId={}, type={}, attemptCount={}, targetStatus={}", reclaimed.id(),
                        reclaimed.assetId(), reclaimed.type(), reclaimed.attemptCount(), targetStatus);
                FileJobType.findByName(reclaimed.type())
                        .ifPresent(jobType -> keysToDelete.addAll(FileObjectPathUtils.attemptCandidateKeys(
                                jobType,
                                reclaimed.assetId(),
                                reclaimed.id(),
                                reclaimed.attemptCount())));
            }
            if (!keysToDelete.isEmpty()) {
                fileObjectStorage.deleteQuietly(keysToDelete);
            }
        }
        return outcome.hasMore();
    }
}
