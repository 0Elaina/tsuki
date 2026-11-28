package com.nep.tsuki.file.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nep.tsuki.file.config.FileJobsProperties;
import com.nep.tsuki.file.constants.FileJobConstants;
import com.nep.tsuki.file.mapper.FileAssetMapper;
import com.nep.tsuki.file.mapper.FileJobMapper;
import com.nep.tsuki.file.mapper.FileVariantMapper;
import com.nep.tsuki.file.model.entity.FileAsset;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.entity.FileVariant;
import com.nep.tsuki.file.model.enums.FileAssetStatus;
import com.nep.tsuki.file.model.enums.FileJobStatus;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.FileVariantStatus;
import com.nep.tsuki.file.processing.FileJobCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileJobTxService {
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 1000;

    private final FileJobMapper fileJobMapper;
    private final FileAssetMapper fileAssetMapper;
    private final FileVariantMapper fileVariantMapper;
    private final FileJobsProperties fileJobsProperties;
    private final TransactionTemplate transactionTemplate;

    public record ClaimedFileJob(FileJob job, FileAsset asset, FileVariant variant) {
    }

    public enum CommitOutcome {
        /** 事务成功提交 */
        COMMITTED,
        /** 目标状态已变化，本次运行任务已标为 CANCELLED */
        CANCELLED,
        /** 租约已失效或任务已被接管，本次放弃落库 */
        LEASE_LOST
    }

    /**
     * 按 Asset -> Variant -> Job 锁序在短事务内原子抢占待执行任务
     */
    public ClaimedFileJob claimForExecution(long jobId, Collection<FileJobType> allowedTypes) {
        Objects.requireNonNull(allowedTypes, "allowedTypes 不能为空");
        if (allowedTypes.isEmpty()) {
            return null;
        }

        List<String> allowedTypeNames = allowedTypes.stream().map(Enum::name).toList();
        OffsetDateTime checkTime = OffsetDateTime.now(ZoneOffset.UTC);

        FileJob snapshot = fileJobMapper.selectById(jobId);
        if (snapshot == null
                || !FileJobStatus.PENDING.name().equals(snapshot.getStatus())
                || !allowedTypeNames.contains(snapshot.getType())
                || snapshot.getAvailableAt().isAfter(checkTime)) {
            return null;
        }

        FileJobType jobType = FileJobType.findByName(snapshot.getType())
                .orElseThrow(() -> new IllegalStateException("未知文件任务类型: " + snapshot.getType()));

        return transactionTemplate.execute(status -> {
            FileAsset asset = fileAssetMapper.selectByIdForUpdate(snapshot.getAssetId());
            FileVariant variant = snapshot.getVariantId() != null
                    ? fileVariantMapper.selectByIdForUpdate(snapshot.getVariantId())
                    : null;

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime leaseUntil = now.plus(fileJobsProperties.getLeaseFor(jobType));

            FileJob claimed = fileJobMapper.claimForExecution(jobId, allowedTypeNames, now, leaseUntil);
            if (claimed == null) {
                return null;
            }
            return new ClaimedFileJob(claimed, asset, variant);
        });
    }

    /**
     * 短事务提交原件校验归档成功：按 Asset -> Job 锁序校验状态并推进资产为 READY（写入 24h 未绑定过期期限）
     */
    public CommitOutcome completeValidateUploadSucceeded(
            FileJob job,
            String originalKey,
            String archivedEtag,
            Map<String, Object> assetMetadata) {
        Objects.requireNonNull(job, "job 不能为空");
        if (!StringUtils.hasText(originalKey)) {
            throw new IllegalArgumentException("originalKey 不能为空");
        }

        return transactionTemplate.execute(status -> {
            FileAsset asset = fileAssetMapper.selectByIdForUpdate(job.getAssetId());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            if (asset == null || !FileAssetStatus.UPLOAD_CONFIRMED.name().equals(asset.getStatus())) {
                int cancelledRows = fileJobMapper.markRunningCancelled(
                        job.getId(),
                        job.getAttemptCount(),
                        null,
                        "资产状态已变化，取消本次归档提交",
                        now);
                return cancelledRows == 1 ? CommitOutcome.CANCELLED : CommitOutcome.LEASE_LOST;
            }

            int updatedJobRows = fileJobMapper.markJobSucceeded(job.getId(), job.getAttemptCount(), now);
            if (updatedJobRows != 1) {
                return CommitOutcome.LEASE_LOST;
            }

            OffsetDateTime unboundExpiresAt = now.plus(FileJobConstants.UNBOUND_ASSET_TTL);
            int updatedAssetRows = fileAssetMapper.markReady(
                    asset.getId(),
                    FileAssetStatus.UPLOAD_CONFIRMED.name(),
                    FileAssetStatus.READY.name(),
                    originalKey,
                    archivedEtag,
                    assetMetadata,
                    unboundExpiresAt,
                    now);
            if (updatedAssetRows != 1) {
                throw new IllegalStateException("切换文件资产为 READY 失败: assetId=" + asset.getId());
            }
            return CommitOutcome.COMMITTED;
        });
    }

    /**
     * 短事务记录原件校验失败：支持重排重试，或按内容不合规/故障耗尽推进资产为 REJECTED / FAILED
     */
    public CommitOutcome recordValidateUploadFailure(
            FileJob job,
            FileJobCode failureCode,
            String failureMessage,
            boolean requeue) {
        Objects.requireNonNull(job, "job 不能为空");
        Objects.requireNonNull(failureCode, "failureCode 不能为空");
        String normalizedFailureMessage = normalizeFailureMessage(failureMessage);

        return transactionTemplate.execute(status -> {
            FileAsset asset = fileAssetMapper.selectByIdForUpdate(job.getAssetId());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            if (asset != null && !FileAssetStatus.UPLOAD_CONFIRMED.name().equals(asset.getStatus())) {
                int cancelledRows = fileJobMapper.markRunningCancelled(
                        job.getId(),
                        job.getAttemptCount(),
                        null,
                        "资产状态已变化，取消本次校验任务",
                        now);
                return cancelledRows == 1 ? CommitOutcome.CANCELLED : CommitOutcome.LEASE_LOST;
            }

            if (requeue) {
                Duration retryDelay = FileJobConstants
                        .retryDelayAfterAttempt(job.getAttemptCount())
                        .orElseThrow(() -> new IllegalStateException(
                                "当前尝试次数不可重排: attemptCount=" + job.getAttemptCount()));
                OffsetDateTime nextAvailableAt = now.plus(retryDelay);
                int updatedRows = fileJobMapper.rescheduleRunningForRetry(
                        job.getId(),
                        job.getAttemptCount(),
                        nextAvailableAt,
                        failureCode.code(),
                        normalizedFailureMessage,
                        now);
                return updatedRows == 1 ? CommitOutcome.COMMITTED : CommitOutcome.LEASE_LOST;
            }

            int updatedRows = fileJobMapper.markRunningFailed(
                    job.getId(),
                    job.getAttemptCount(),
                    failureCode.code(),
                    normalizedFailureMessage,
                    now);
            if (updatedRows != 1) {
                return CommitOutcome.LEASE_LOST;
            }

            if (asset != null) {
                FileAssetStatus targetStatus = failureCode.contentRejected()
                        ? FileAssetStatus.REJECTED
                        : FileAssetStatus.FAILED;
                fileAssetMapper.markValidationTerminalFailure(
                        asset.getId(),
                        FileAssetStatus.UPLOAD_CONFIRMED.name(),
                        targetStatus.name(),
                        failureCode.code(),
                        normalizedFailureMessage,
                        now);
            }
            return CommitOutcome.COMMITTED;
        });
    }

    /**
     * 短事务提交衍生物生成成功：按 Asset -> Variant -> Job 锁序校验状态并推进衍生物为 READY
     */
    public CommitOutcome completeGenerateVariantSucceeded(
            FileJob job,
            String objectKey,
            String contentType,
            long byteSize,
            String etag,
            Map<String, Object> metadata) {
        Objects.requireNonNull(job, "job 不能为空");
        Objects.requireNonNull(job.getVariantId(), "variantId 不能为空");
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }

        return transactionTemplate.execute(status -> {
            FileAsset asset = fileAssetMapper.selectByIdForUpdate(job.getAssetId());
            FileVariant variant = fileVariantMapper.selectByIdForUpdate(job.getVariantId());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            if (asset == null
                    || !FileAssetStatus.READY.name().equals(asset.getStatus())
                    || variant == null
                    || !FileVariantStatus.PENDING.name().equals(variant.getStatus())) {
                int cancelledRows = fileJobMapper.markRunningCancelled(
                        job.getId(),
                        job.getAttemptCount(),
                        null,
                        "资产或衍生物状态已变化，取消本次衍生物落库",
                        now);
                return cancelledRows == 1 ? CommitOutcome.CANCELLED : CommitOutcome.LEASE_LOST;
            }

            int updatedJobRows = fileJobMapper.markJobSucceeded(job.getId(), job.getAttemptCount(), now);
            if (updatedJobRows != 1) {
                return CommitOutcome.LEASE_LOST;
            }

            int updatedVariantRows = fileVariantMapper.markReady(
                    variant.getId(),
                    FileVariantStatus.PENDING.name(),
                    FileVariantStatus.READY.name(),
                    objectKey,
                    contentType,
                    byteSize,
                    etag,
                    metadata,
                    now);
            if (updatedVariantRows != 1) {
                throw new IllegalStateException("切换文件衍生物为 READY 失败: variantId=" + variant.getId());
            }
            return CommitOutcome.COMMITTED;
        });
    }

    /**
     * 短事务记录衍生物生成失败：仅推进衍生物自身状态，不影响原件资产状态
     */
    public CommitOutcome recordGenerateVariantFailure(
            FileJob job,
            FileJobCode failureCode,
            String failureMessage,
            boolean requeue) {
        Objects.requireNonNull(job, "job 不能为空");
        Objects.requireNonNull(job.getVariantId(), "variantId 不能为空");
        Objects.requireNonNull(failureCode, "failureCode 不能为空");
        String normalizedFailureMessage = normalizeFailureMessage(failureMessage);

        return transactionTemplate.execute(status -> {
            FileAsset asset = fileAssetMapper.selectByIdForUpdate(job.getAssetId());
            FileVariant variant = fileVariantMapper.selectByIdForUpdate(job.getVariantId());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            if (asset == null
                    || !FileAssetStatus.READY.name().equals(asset.getStatus())
                    || variant == null
                    || !FileVariantStatus.PENDING.name().equals(variant.getStatus())) {
                int cancelledRows = fileJobMapper.markRunningCancelled(
                        job.getId(),
                        job.getAttemptCount(),
                        null,
                        "资产或衍生物状态已变化，取消本次衍生物生成任务",
                        now);
                return cancelledRows == 1 ? CommitOutcome.CANCELLED : CommitOutcome.LEASE_LOST;
            }

            if (requeue) {
                Duration retryDelay = FileJobConstants
                        .retryDelayAfterAttempt(job.getAttemptCount())
                        .orElseThrow(() -> new IllegalStateException(
                                "当前尝试次数不可重排: attemptCount=" + job.getAttemptCount()));
                OffsetDateTime nextAvailableAt = now.plus(retryDelay);
                int updatedRows = fileJobMapper.rescheduleRunningForRetry(
                        job.getId(),
                        job.getAttemptCount(),
                        nextAvailableAt,
                        failureCode.code(),
                        normalizedFailureMessage,
                        now);
                return updatedRows == 1 ? CommitOutcome.COMMITTED : CommitOutcome.LEASE_LOST;
            }

            int updatedRows = fileJobMapper.markRunningFailed(
                    job.getId(),
                    job.getAttemptCount(),
                    failureCode.code(),
                    normalizedFailureMessage,
                    now);
            if (updatedRows != 1) {
                return CommitOutcome.LEASE_LOST;
            }

            fileVariantMapper.markFailed(
                    variant.getId(),
                    FileVariantStatus.PENDING.name(),
                    FileVariantStatus.FAILED.name(),
                    failureCode.code(),
                    normalizedFailureMessage,
                    now);
            return CommitOutcome.COMMITTED;
        });
    }

    /**
     * 短事务提交整份资产物理清理成功：按 Asset -> Variants -> Job 锁序写入 purged_at 并将资产推进为 DELETED
     */
    public CommitOutcome completeCleanupAssetSucceeded(FileJob job) {
        Objects.requireNonNull(job, "job 不能为空");

        return transactionTemplate.execute(status -> {
            FileAsset asset = fileAssetMapper.selectByIdForUpdate(job.getAssetId());
            List<FileVariant> variants = fileVariantMapper.selectByAssetIdForUpdate(job.getAssetId());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            if (asset == null || FileAssetStatus.DELETED.name().equals(asset.getStatus())) {
                int cancelledRows = fileJobMapper.markRunningCancelled(
                        job.getId(),
                        job.getAttemptCount(),
                        null,
                        "资产已处于 DELETED 状态，结束重复清理任务",
                        now);
                return cancelledRows == 1 ? CommitOutcome.CANCELLED : CommitOutcome.LEASE_LOST;
            }
            if (!FileAssetStatus.DELETING.name().equals(asset.getStatus())) {
                int cancelledRows = fileJobMapper.markRunningCancelled(
                        job.getId(),
                        job.getAttemptCount(),
                        null,
                        "资产状态非 DELETING，取消清理任务: status=" + asset.getStatus(),
                        now);
                return cancelledRows == 1 ? CommitOutcome.CANCELLED : CommitOutcome.LEASE_LOST;
            }

            int updatedJobRows = fileJobMapper.markJobSucceeded(job.getId(), job.getAttemptCount(), now);
            if (updatedJobRows != 1) {
                return CommitOutcome.LEASE_LOST;
            }

            for (FileVariant variant : variants) {
                if (variant.getPurgedAt() == null) {
                    fileVariantMapper.update(null, new LambdaUpdateWrapper<FileVariant>()
                            .eq(FileVariant::getId, variant.getId())
                            .isNull(FileVariant::getPurgedAt)
                            .set(FileVariant::getStatus, FileVariantStatus.WITHDRAWN.name())
                            .set(variant.getWithdrawnAt() == null, FileVariant::getWithdrawnAt, now)
                            .set(FileVariant::getPurgedAt, now)
                            .set(FileVariant::getUpdatedAt, now));
                }
            }

            fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                    .eq(FileAsset::getId, asset.getId())
                    .eq(FileAsset::getStatus, FileAssetStatus.DELETING.name())
                    .set(FileAsset::getStatus, FileAssetStatus.DELETED.name())
                    .set(FileAsset::getPurgedAt, now)
                    .set(FileAsset::getUpdatedAt, now));
            return CommitOutcome.COMMITTED;
        });
    }

    /**
     * 短事务提交单个衍生物物理清理成功：按 Asset -> Variant -> Job 锁序写入 purged_at（保留 WITHDRAWN 状态）
     */
    public CommitOutcome completeCleanupVariantSucceeded(FileJob job) {
        Objects.requireNonNull(job, "job 不能为空");
        Objects.requireNonNull(job.getVariantId(), "variantId 不能为空");

        return transactionTemplate.execute(status -> {
            fileAssetMapper.selectByIdForUpdate(job.getAssetId());
            FileVariant variant = fileVariantMapper.selectByIdForUpdate(job.getVariantId());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            if (variant == null || variant.getPurgedAt() != null
                    || !FileVariantStatus.WITHDRAWN.name().equals(variant.getStatus())) {
                int cancelledRows = fileJobMapper.markRunningCancelled(
                        job.getId(),
                        job.getAttemptCount(),
                        null,
                        "衍生物已完成清理或状态非 WITHDRAWN，取消清理任务",
                        now);
                return cancelledRows == 1 ? CommitOutcome.CANCELLED : CommitOutcome.LEASE_LOST;
            }

            int updatedJobRows = fileJobMapper.markJobSucceeded(job.getId(), job.getAttemptCount(), now);
            if (updatedJobRows != 1) {
                return CommitOutcome.LEASE_LOST;
            }

            fileVariantMapper.update(null, new LambdaUpdateWrapper<FileVariant>()
                    .eq(FileVariant::getId, variant.getId())
                    .eq(FileVariant::getStatus, FileVariantStatus.WITHDRAWN.name())
                    .isNull(FileVariant::getPurgedAt)
                    .set(FileVariant::getPurgedAt, now)
                    .set(FileVariant::getUpdatedAt, now));
            return CommitOutcome.COMMITTED;
        });
    }

    /**
     * 短事务重排失败的清理任务：同一任务按 30s/2m/15m/1h/6h... 持续自动退避，不进入终态失败
     */
    public CommitOutcome rescheduleCleanupRetry(FileJob job, FileJobCode failureCode, String failureMessage) {
        Objects.requireNonNull(job, "job 不能为空");
        Objects.requireNonNull(failureCode, "failureCode 不能为空");
        String normalizedFailureMessage = normalizeFailureMessage(failureMessage);

        return transactionTemplate.execute(status -> {
            fileAssetMapper.selectByIdForUpdate(job.getAssetId());
            if (job.getVariantId() != null) {
                fileVariantMapper.selectByIdForUpdate(job.getVariantId());
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            Duration retryDelay = FileJobConstants.cleanupRetryDelayAfterAttempt(job.getAttemptCount());
            OffsetDateTime nextAvailableAt = now.plus(retryDelay);
            int updatedRows = fileJobMapper.rescheduleRunningForRetry(
                    job.getId(),
                    job.getAttemptCount(),
                    nextAvailableAt,
                    failureCode.code(),
                    normalizedFailureMessage,
                    now);
            return updatedRows == 1 ? CommitOutcome.COMMITTED : CommitOutcome.LEASE_LOST;
        });
    }

    /**
     * 短事务取消仍在运行中的任务（目标记录已撤回或删除时调用）
     */
    public CommitOutcome cancelRunningJob(FileJob job, String failureCode, String reason) {
        Objects.requireNonNull(job, "job 不能为空");
        String normalizedReason = normalizeFailureMessage(reason);

        return transactionTemplate.execute(status -> {
            fileAssetMapper.selectByIdForUpdate(job.getAssetId());
            if (job.getVariantId() != null) {
                fileVariantMapper.selectByIdForUpdate(job.getVariantId());
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            int updatedRows = fileJobMapper.markRunningCancelled(
                    job.getId(),
                    job.getAttemptCount(),
                    failureCode,
                    normalizedReason,
                    now);
            return updatedRows == 1 ? CommitOutcome.CANCELLED : CommitOutcome.LEASE_LOST;
        });
    }

    public static String normalizeFailureMessage(String failureMessage) {
        if (!StringUtils.hasText(failureMessage)) {
            return null;
        }
        String stripped = failureMessage.strip();
        if (stripped.length() <= MAX_FAILURE_MESSAGE_LENGTH) {
            return stripped;
        }
        return stripped.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }
}
