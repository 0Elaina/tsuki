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

/**
 * 文件异步任务短事务协调服务，所有跨表写事务严格按 Asset -> Variant -> Job 顺序加锁以避免死锁
 */
@Service
@RequiredArgsConstructor
public class FileJobTxService {
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 1000;

    private final FileJobMapper fileJobMapper;
    private final FileAssetMapper fileAssetMapper;
    private final FileVariantMapper fileVariantMapper;
    private final FileJobsProperties fileJobsProperties;
    private final TransactionTemplate transactionTemplate;

    /**
     * 任务抢占结果上下文
     *
     * @param job     已进入 RUNNING 的任务记录
     * @param asset   关联的文件资产记录
     * @param variant 关联的衍生物记录（非衍生物任务为 {@code null}）
     */
    public record ClaimedFileJob(FileJob job, FileAsset asset, FileVariant variant) {
    }

    /**
     * 任务短事务提交结果
     */
    public enum CommitOutcome {
        /** 事务成功落库 */
        COMMITTED,
        /** 关联资产或衍生物状态已变化，本次运行任务已标为 CANCELLED */
        CANCELLED,
        /** 执行租约已失效或任务已被接管，本次放弃落库 */
        LEASE_LOST
    }

    /**
     * 在短事务内按 Asset -> Variant -> Job 锁序原子抢占指定类型的待执行任务
     *
     * @param jobId        待执行任务主键 ID
     * @param allowedTypes 当前消费队列允许领取的任务类型集合
     * @return 抢占成功返回上下文；若任务不存在、类型不符、状态不可抢占或未到执行时间则返回 {@code null}
     */
    public ClaimedFileJob claimForExecution(long jobId, Collection<FileJobType> allowedTypes) {
        Objects.requireNonNull(allowedTypes, "allowedTypes 不能为空");
        if (allowedTypes.isEmpty()) {
            return null;
        }

        List<String> allowedTypeNames = allowedTypes.stream().map(Enum::name).toList();
        OffsetDateTime checkTime = OffsetDateTime.now(ZoneOffset.UTC);

        // 先做无锁预检，快速过滤重复消息、不匹配类型与尚未到点的延迟重试任务
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
            // 严格按 Asset -> Variant -> Job 锁序加行锁，与业务绑定/撤回事务保持一致
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
     * 在短事务内提交原件校验与归档成功结果，将资产推进为 READY 并写入 24 小时未绑定过期截止时间
     *
     * @param job           当前运行中的校验任务
     * @param originalKey   归档后的正式原件对象键
     * @param archivedEtag  归档后的正式原件 ETag
     * @param assetMetadata 探测得到的原件扩展元数据
     * @return 提交结果状态
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

            // 资产在校验期间被删除或状态变化时，将本次运行任务标为 CANCELLED，由调用方回收归档产物
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
     * 在短事务内记录原件校验失败结果：按需重排等待重试，或在终态失败时将资产推进为 REJECTED 或 FAILED
     *
     * @param job            当前运行中的校验任务
     * @param failureCode    失败原因码
     * @param failureMessage 失败补充详情（超长自动截断）
     * @param requeue        是否重排等待重试；{@code false} 表示进入终态失败
     * @return 提交结果状态
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

            // 内容不合规进入 REJECTED，存储缺失或重试耗尽进入 FAILED
            if (asset != null) {
                FileAssetStatus targetStatus = resolveValidateTerminalAssetStatus(failureCode);
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
     * 在短事务内提交衍生物生成成功结果，按 Asset -> Variant -> Job 锁序推进衍生物为 READY
     *
     * @param job         当前运行中的衍生物生成任务
     * @param objectKey   衍生物产物的尝试级对象键
     * @param contentType 实际输出 MIME 类型
     * @param byteSize    产物字节大小
     * @param etag        产物对象 ETag
     * @param metadata    产物扩展元数据（图片宽高写入此 JSONB）
     * @return 提交结果状态
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
     * 在短事务内记录衍生物生成失败结果（仅推进衍生物自身状态，不影响已就绪原件资产）
     *
     * @param job            当前运行中的衍生物生成任务
     * @param failureCode    失败原因码
     * @param failureMessage 失败补充详情
     * @param requeue        是否重排等待重试
     * @return 提交结果状态
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
     * 在短事务内提交整份资产物理清理成功结果，写入 purged_at 并将资产推进为 DELETED
     *
     * @param job 当前运行中的资产清理任务
     * @return 提交结果状态
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

            // 为该资产下所有未标记 purged_at 的衍生物补齐撤回与物理清理时间戳
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
     * 在短事务内提交单个已撤回衍生物物理清理成功结果，写入 purged_at 并保持 WITHDRAWN 状态
     *
     * @param job 当前运行中的衍生物清理任务
     * @return 提交结果状态
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
     * 在短事务内重排失败的清理任务：同一任务持续按 30s/2min/15min/1h/6h... 退避，不进入终态失败
     *
     * @param job            当前运行中的清理任务
     * @param failureCode    失败原因码
     * @param failureMessage 最近一次失败详情
     * @return 提交结果状态
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
     * 在短事务内将当前运行中的任务标记为 CANCELLED（目标资产或衍生物已被撤回或删除时调用）
     *
     * @param job         当前运行中的任务
     * @param failureCode 可选诊断码
     * @param reason      取消原因说明
     * @return 提交结果状态
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

    /**
     * 根据原件校验失败原因码决定资产终态：内容不合规归 REJECTED，缺失或故障耗尽归 FAILED
     */
    private FileAssetStatus resolveValidateTerminalAssetStatus(FileJobCode failureCode) {
        if (FileJobCode.INVALID_IMAGE.equals(failureCode) || FileJobCode.IMAGE_TOO_LARGE.equals(failureCode)) {
            return FileAssetStatus.REJECTED;
        }
        return FileAssetStatus.FAILED;
    }

    /**
     * 规范化并截断失败详情文本，避免异常栈过长撑大数据库记录
     *
     * @param failureMessage 原始失败说明
     * @return 规范化后的失败详情，空白时返回 {@code null}
     */
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
