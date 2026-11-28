package com.nep.tsuki.file.task;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.nep.tsuki.file.config.FileJobsProperties;
import com.nep.tsuki.file.constants.FileJobConstants;
import com.nep.tsuki.file.mapper.FileAssetMapper;
import com.nep.tsuki.file.mapper.FileJobMapper;
import com.nep.tsuki.file.mapper.FileVariantMapper;
import com.nep.tsuki.file.model.entity.FileAsset;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.entity.FileVariant;
import com.nep.tsuki.file.model.enums.FileAssetStatus;
import com.nep.tsuki.file.model.enums.FileJobQueue;
import com.nep.tsuki.file.model.enums.FileJobStatus;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.model.enums.FileVariantStatus;
import com.nep.tsuki.file.processing.FileJobCode;
import com.nep.tsuki.file.storage.FileObjectStorage;
import com.nep.tsuki.file.utils.FileObjectPathUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 运行中文件任务执行租期超时回收器，按 Asset -> Variant -> Job 锁序安全接管僵尸任务并清理尝试产物
 */
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

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(this::reapExpiredJobs, fileJobsProperties.getReapInterval());
    }

    /**
     * 定时任务主入口：循环分批扫描并回收租期已过期的运行中文件任务
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
     * 扫描并回收单批次租期过期的任务：事务内逐条推进状态，事务外按桶静默清理残留的尝试产物
     *
     * @return {@code true} 表示本批已达上限可能仍有积压；{@code false} 表示本轮已排空
     */
    private boolean reapSingleBatch() {
        OffsetDateTime scanNow = OffsetDateTime.now(ZoneOffset.UTC);
        List<Long> candidateJobIds = fileJobMapper.selectExpiredRunningIds(scanNow, FileJobConstants.REAP_BATCH_SIZE);
        if (candidateJobIds.isEmpty()) {
            return false;
        }

        // 按逻辑存储桶归集需要回收的尝试级产物键，避免在数据库事务内发起 S3 网络 I/O
        Map<FileStorageScope, List<String>> artifactsByScope = new EnumMap<>(FileStorageScope.class);
        for (Long jobId : candidateJobIds) {
            reapSingleJob(jobId, artifactsByScope);
        }

        artifactsByScope.forEach(fileObjectStorage::deleteQuietly);
        return candidateJobIds.size() == FileJobConstants.REAP_BATCH_SIZE;
    }

    /**
     * 在独立短事务内按 Asset -> Variant -> Job 锁序回收单个租期超时任务
     *
     * @param jobId            候选超时任务主键 ID
     * @param artifactsByScope 待回收尝试产物的按桶收集映射
     */
    private void reapSingleJob(long jobId, Map<FileStorageScope, List<String>> artifactsByScope) {
        FileJob snapshot = fileJobMapper.selectById(jobId);
        if (snapshot == null
                || !FileJobStatus.RUNNING.name().equals(snapshot.getStatus())
                || snapshot.getLeaseUntil() == null) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            // 严格遵循 Asset -> Variant -> Job 锁序，避免与业务绑定/撤回事务产生死锁
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
                return;
            }

            Optional<FileJobType> jobTypeOptional = FileJobType.findByName(job.getType());
            if (jobTypeOptional.isEmpty()) {
                int updated = fileJobMapper.markExpiredFailed(
                        job.getId(),
                        job.getAttemptCount(),
                        FileJobCode.LEASE_EXPIRED.code(),
                        "未知任务类型且执行租期已超时",
                        now);
                if (updated == 1) {
                    log.warn("回收未知类型超时文件任务: jobId={}, assetId={}, type={}, attemptCount={}",
                            job.getId(), job.getAssetId(), job.getType(), job.getAttemptCount());
                }
                return;
            }

            FileJobType jobType = jobTypeOptional.get();

            // 1. 若目标资产或衍生物已撤回/删除/终态，直接标为 CANCELLED 并登记清理本次尝试产物
            if (shouldCancelExpiredJob(jobType, asset, variant)) {
                int updated = fileJobMapper.markExpiredCancelled(
                        job.getId(),
                        job.getAttemptCount(),
                        null,
                        "任务租期到期且目标记录已失效，标记为已取消",
                        now);
                if (updated == 1) {
                    collectAttemptArtifact(jobType, job, variant, artifactsByScope);
                    log.warn("回收已失效目标的超时任务并取消: jobId={}, assetId={}, variantId={}, type={}, attemptCount={}",
                            job.getId(), job.getAssetId(), job.getVariantId(), jobType, job.getAttemptCount());
                }
                return;
            }

            // 2. 清理队列任务采用持续自动退避，永不进入终态失败，达到 6h 稳态时升级为 ERROR 日志
            if (jobType.getQueue() == FileJobQueue.CLEANUP) {
                Duration backoff = FileJobConstants.cleanupRetryDelayAfterAttempt(job.getAttemptCount());
                OffsetDateTime nextAvailableAt = now.plus(backoff);
                int updated = fileJobMapper.rescheduleExpiredForRetry(
                        job.getId(),
                        job.getAttemptCount(),
                        nextAvailableAt,
                        FileJobCode.LEASE_EXPIRED.code(),
                        "清理任务执行租期超时，已按退避策略重排",
                        now);
                if (updated == 1) {
                    if (FileJobConstants.isCleanupSteadyBackoff(job.getAttemptCount())) {
                        log.error("清理任务租期超时已进入 6h 稳态退避，请排查对象存储权限或连接故障: jobId={}, assetId={}, variantId={}, type={}, attemptCount={}",
                                job.getId(), job.getAssetId(), job.getVariantId(), jobType, job.getAttemptCount());
                    } else {
                        log.warn("清理任务租期超时，已重排等待重试: jobId={}, assetId={}, variantId={}, type={}, attemptCount={}",
                                job.getId(), job.getAssetId(), job.getVariantId(), jobType, job.getAttemptCount());
                    }
                }
                return;
            }

            // 3. 交互队列任务在重试预算内按 30s / 120s 退避重排
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
                if (updated == 1) {
                    collectAttemptArtifact(jobType, job, variant, artifactsByScope);
                    log.warn("回收租期超时交互任务并重排: jobId={}, assetId={}, variantId={}, type={}, attemptCount={}",
                            job.getId(), job.getAssetId(), job.getVariantId(), jobType, job.getAttemptCount());
                }
                return;
            }

            // 4. 交互队列任务耗尽重试次数：标为 FAILED 并同步推进对应资产或衍生物终态
            int updated = fileJobMapper.markExpiredFailed(
                    job.getId(),
                    job.getAttemptCount(),
                    FileJobCode.LEASE_EXPIRED.code(),
                    "任务执行租期已超时，达到最大尝试次数",
                    now);
            if (updated == 1) {
                advanceTerminalFailureTarget(jobType, asset, variant, now);
                collectAttemptArtifact(jobType, job, variant, artifactsByScope);
                log.warn("回收租期超时交互任务并置为终态失败: jobId={}, assetId={}, variantId={}, type={}, attemptCount={}",
                        job.getId(), job.getAssetId(), job.getVariantId(), jobType, job.getAttemptCount());
            }
        });
    }

    private void collectAttemptArtifact(
            FileJobType jobType,
            FileJob job,
            FileVariant variant,
            Map<FileStorageScope, List<String>> artifactsByScope) {
        if (job.getAttemptCount() == null || job.getAttemptCount() <= 0) {
            return;
        }
        switch (jobType) {
            case VALIDATE_UPLOAD -> artifactsByScope
                    .computeIfAbsent(FileStorageScope.PRIVATE, key -> new ArrayList<>())
                    .add(FileObjectPathUtils.originalKey(job.getAssetId(), job.getId(), job.getAttemptCount()));
            case GENERATE_VARIANT -> {
                if (job.getVariantId() != null && variant != null && StringUtils.hasText(variant.getStorageScope())) {
                    artifactsByScope
                            .computeIfAbsent(FileStorageScope.valueOf(variant.getStorageScope()),
                                    key -> new ArrayList<>())
                            .add(FileObjectPathUtils.variantKey(job.getAssetId(), job.getVariantId(), job.getId(),
                                    job.getAttemptCount()));
                }
            }
            case CLEANUP_ASSET, CLEANUP_VARIANT -> {
                // 清理任务不产生新的对象存储产物
            }
        }
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
}
