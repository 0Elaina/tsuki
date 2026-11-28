package com.nep.tsuki.file.task;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.nep.tsuki.file.config.FileJobsProperties;
import com.nep.tsuki.file.constants.FileJobConstants;
import com.nep.tsuki.file.mapper.FileAssetMapper;
import com.nep.tsuki.file.mapper.FileJobMapper;
import com.nep.tsuki.file.mapper.FileVariantMapper;
import com.nep.tsuki.file.model.dto.StorageObjectRef;
import com.nep.tsuki.file.model.entity.FileAsset;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.entity.FileVariant;
import com.nep.tsuki.file.model.enums.FileAssetStatus;
import com.nep.tsuki.file.model.enums.FileJobStatus;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.FileVariantStatus;
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
    private final FileVariantMapper fileVariantMapper;
    private final FileObjectStorage fileObjectStorage;
    private final TransactionTemplate transactionTemplate;
    private final FileJobsProperties fileJobsProperties;

    private record ReapedJobOutcome(
            long jobId,
            long assetId,
            Long variantId,
            FileJobType jobType,
            int attemptCount,
            FileJobStatus targetStatus,
            StorageObjectRef artifactToDelete) {
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(this::reapExpiredJobs, fileJobsProperties.getReapInterval());
    }

    /**
     * 定时分批扫描并回收租期已过期的运行中文件任务
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

    private boolean reapSingleBatch() {
        OffsetDateTime scanNow = OffsetDateTime.now(ZoneOffset.UTC);
        List<Long> candidateJobIds = fileJobMapper.selectExpiredRunningIds(scanNow, FileJobConstants.REAP_BATCH_SIZE);
        if (candidateJobIds.isEmpty()) {
            return false;
        }

        List<StorageObjectRef> artifactsToDelete = new ArrayList<>();
        for (Long jobId : candidateJobIds) {
            ReapedJobOutcome outcome = reapSingleJob(jobId);
            if (outcome == null) {
                continue;
            }
            logReapedJob(outcome);
            if (outcome.artifactToDelete() != null) {
                artifactsToDelete.add(outcome.artifactToDelete());
            }
        }

        if (!artifactsToDelete.isEmpty()) {
            fileObjectStorage.deleteQuietly(artifactsToDelete);
        }

        return candidateJobIds.size() == FileJobConstants.REAP_BATCH_SIZE;
    }

    private ReapedJobOutcome reapSingleJob(long jobId) {
        FileJob snapshot = fileJobMapper.selectById(jobId);
        if (snapshot == null
                || !FileJobStatus.RUNNING.name().equals(snapshot.getStatus())
                || snapshot.getLeaseUntil() == null) {
            return null;
        }

        // 严格遵循 Asset -> Variant -> Job 锁序，避免与业务绑定/撤回事务死锁
        return transactionTemplate.execute(status -> {
            FileAsset asset = fileAssetMapper.selectByIdForUpdate(snapshot.getAssetId());
            FileVariant variant = snapshot.getVariantId() != null
                    ? fileVariantMapper.selectByIdForUpdate(snapshot.getVariantId())
                    : null;
            FileJob job = fileJobMapper.selectByIdForUpdate(jobId);

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            if (job == null
                    || !FileJobStatus.RUNNING.name().equals(job.getStatus())
                    || job.getLeaseUntil() == null
                    || job.getLeaseUntil().isAfter(now)) {
                return null;
            }

            Optional<FileJobType> jobTypeOptional = FileJobType.findByName(job.getType());
            if (jobTypeOptional.isEmpty()) {
                int updated = fileJobMapper.markExpiredFailed(
                        job.getId(),
                        job.getAttemptCount(),
                        FileJobCode.LEASE_EXPIRED.code(),
                        "未知任务类型且执行租期已超时",
                        now);
                return updated == 1
                        ? new ReapedJobOutcome(job.getId(), job.getAssetId(), job.getVariantId(), null,
                                job.getAttemptCount(), FileJobStatus.FAILED, null)
                        : null;
            }

            FileJobType jobType = jobTypeOptional.get();
            StorageObjectRef artifact = FileObjectPathUtils.attemptArtifact(job, variant).orElse(null);

            if (shouldCancelExpiredJob(jobType, asset, variant)) {
                int updated = fileJobMapper.markExpiredCancelled(
                        job.getId(),
                        job.getAttemptCount(),
                        null,
                        "任务租期到期且目标记录已失效，标记为已取消",
                        now);
                return updated == 1
                        ? new ReapedJobOutcome(job.getId(), job.getAssetId(), job.getVariantId(), jobType,
                                job.getAttemptCount(), FileJobStatus.CANCELLED, artifact)
                        : null;
            }

            if (jobType.isCleanup()) {
                Duration backoff = FileJobConstants.cleanupRetryDelayAfterAttempt(job.getAttemptCount());
                OffsetDateTime nextAvailableAt = now.plus(backoff);
                int updated = fileJobMapper.rescheduleExpiredForRetry(
                        job.getId(),
                        job.getAttemptCount(),
                        nextAvailableAt,
                        FileJobCode.LEASE_EXPIRED.code(),
                        "清理任务执行租期超时，已按退避策略重排",
                        now);
                return updated == 1
                        ? new ReapedJobOutcome(job.getId(), job.getAssetId(), job.getVariantId(), jobType,
                                job.getAttemptCount(), FileJobStatus.PENDING, null)
                        : null;
            }

            Optional<Duration> retryDelay = FileJobConstants.retryDelayAfterAttempt(job.getAttemptCount());
            if (retryDelay.isPresent()) {
                OffsetDateTime nextAvailableAt = now.plus(retryDelay.get());
                int updated = fileJobMapper.rescheduleExpiredForRetry(
                        job.getId(),
                        job.getAttemptCount(),
                        nextAvailableAt,
                        FileJobCode.LEASE_EXPIRED.code(),
                        "任务执行租期已超时，已重排等待重试",
                        now);
                return updated == 1
                        ? new ReapedJobOutcome(job.getId(), job.getAssetId(), job.getVariantId(), jobType,
                                job.getAttemptCount(), FileJobStatus.PENDING, artifact)
                        : null;
            }

            int updated = fileJobMapper.markExpiredFailed(
                    job.getId(),
                    job.getAttemptCount(),
                    FileJobCode.LEASE_EXPIRED.code(),
                    "任务执行租期已超时，达到最大尝试次数",
                    now);
            if (updated != 1) {
                return null;
            }

            advanceTerminalFailureTarget(jobType, asset, variant, now);
            return new ReapedJobOutcome(job.getId(), job.getAssetId(), job.getVariantId(), jobType,
                    job.getAttemptCount(), FileJobStatus.FAILED, artifact);
        });
    }

    private boolean shouldCancelExpiredJob(FileJobType jobType, FileAsset asset, FileVariant variant) {
        return switch (jobType) {
            case VALIDATE_UPLOAD -> asset == null || !FileAssetStatus.UPLOAD_CONFIRMED.name().equals(asset.getStatus());
            case GENERATE_VARIANT -> asset == null
                    || !FileAssetStatus.READY.name().equals(asset.getStatus())
                    || variant == null
                    || !FileVariantStatus.PENDING.name().equals(variant.getStatus());
            case CLEANUP_ASSET -> asset != null && FileAssetStatus.DELETED.name().equals(asset.getStatus());
            case CLEANUP_VARIANT -> variant != null && variant.getPurgedAt() != null;
        };
    }

    private void advanceTerminalFailureTarget(
            FileJobType jobType,
            FileAsset asset,
            FileVariant variant,
            OffsetDateTime now) {
        switch (jobType) {
            case VALIDATE_UPLOAD -> {
                if (asset != null) {
                    fileAssetMapper.markValidationTerminalFailure(
                            asset.getId(),
                            FileAssetStatus.UPLOAD_CONFIRMED.name(),
                            FileAssetStatus.FAILED.name(),
                            FileJobCode.LEASE_EXPIRED.code(),
                            "任务执行租期已超时，达到最大尝试次数",
                            now);
                }
            }
            case GENERATE_VARIANT -> {
                if (variant != null) {
                    fileVariantMapper.markFailed(
                            variant.getId(),
                            FileVariantStatus.PENDING.name(),
                            FileVariantStatus.FAILED.name(),
                            FileJobCode.LEASE_EXPIRED.code(),
                            "任务执行租期已超时，达到最大尝试次数",
                            now);
                }
            }
            case CLEANUP_ASSET, CLEANUP_VARIANT -> {
                // 清理任务持续自动退避，不进入终态失败
            }
        }
    }

    private void logReapedJob(ReapedJobOutcome outcome) {
        if (outcome.jobType() != null
                && outcome.jobType().isCleanup()
                && outcome.targetStatus() == FileJobStatus.PENDING
                && FileJobConstants.isCleanupSteadyBackoff(outcome.attemptCount())) {
            log.error("清理任务租期超时已进入 6h 稳态退避，请排查对象存储权限或连接故障: jobId={}, assetId={}, variantId={}, type={}, attemptCount={}",
                    outcome.jobId(), outcome.assetId(), outcome.variantId(), outcome.jobType(), outcome.attemptCount());
            return;
        }
        log.warn("回收租期超时文件任务: jobId={}, assetId={}, variantId={}, type={}, attemptCount={}, targetStatus={}",
                outcome.jobId(), outcome.assetId(), outcome.variantId(), outcome.jobType(),
                outcome.attemptCount(), outcome.targetStatus());
    }
}
