package com.nep.tsuki.file.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.processing.FileJobCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileJobTxService {
    private final FileJobMapper fileJobMapper;
    private final FileAssetMapper fileAssetMapper;
    private final FileVariantMapper fileVariantMapper;
    private final FileJobsProperties fileJobsProperties;
    private final TransactionTemplate transactionTemplate;

    public record ClaimedFileJob(FileJob job, FileAsset asset) {
    }

    /**
     * 在短事务中原子抢占指定类型的文件处理任务，并加载其关联的文件资产
     *
     * @param jobId        待执行的文件任务主键 ID
     * @param expectedType 期望的任务类型，用于校验类型匹配并计算租约时长
     * @return 抢占成功返回包含任务与资产的包装对象；若任务不存在、类型不符或状态不可抢占则返回 null
     */
    public ClaimedFileJob claimForExecution(long jobId, FileJobType expectedType) {
        Objects.requireNonNull(expectedType, "expectedType 不能为空");
        return transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime leaseUntil = now.plus(fileJobsProperties.getLeaseFor(expectedType));

            FileJob job = fileJobMapper.claimForExecution(
                    jobId,
                    expectedType.name(),
                    now,
                    leaseUntil);
            if (job == null) {
                return null;
            }

            FileAsset asset = fileAssetMapper.selectById(job.getAssetId());
            return new ClaimedFileJob(job, asset);
        });
    }

    /**
     * 在短事务中完成文件任务：标记任务成功、批量落库交付规格，并在主任务场景下将资产切换为 READY
     *
     * @param job           当前执行中的文件任务
     * @param asset         关联的文件资产
     * @param originalKey   归档后的原始对象存储键（仅主任务推进资产状态时必填）
     * @param archivedEtag  归档后的原始对象 ETag
     * @param assetMetadata 探测到的原件扩展元数据（可为 null，仅主任务推进资产状态时写入）
     * @param variants      本次处理生成的文件规格列表（仅归档原件时可传空列表）
     * @return 事务成功提交返回 true；若任务租约已失效或状态不匹配则返回 false
     */
    public boolean completeSucceeded(
            FileJob job,
            FileAsset asset,
            String originalKey,
            String archivedEtag,
            Map<String, Object> assetMetadata,
            List<FileVariant> variants) {
        Objects.requireNonNull(job, "job 不能为空");
        Objects.requireNonNull(asset, "asset 不能为空");
        Objects.requireNonNull(variants, "variants 不能为空");

        Boolean committed = transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            int updatedJobRows = fileJobMapper.markJobSucceeded(
                    job.getId(),
                    job.getAttemptCount(),
                    now);
            if (updatedJobRows != 1) {
                return Boolean.FALSE;
            }

            if (!variants.isEmpty()) {
                for (FileVariant variant : variants) {
                    if (variant.getAssetId() == null) {
                        variant.setAssetId(asset.getId());
                    }
                }
                // 不能直接调用 MyBatis-Plus 的 insert(Collection)：同事务前序更新已绑定 SIMPLE 执行器
                // MP 批量方法会切 BATCH 触发 ExecutorType 冲突
                fileVariantMapper.insertBatch(variants, now);
            }

            boolean advancesAssetStatus = FileJobType
                    .findByName(job.getType())
                    .map(FileJobType::isAdvancesAssetStatus)
                    .orElse(false);
            if (advancesAssetStatus) {
                if (!StringUtils.hasText(originalKey)) {
                    throw new IllegalArgumentException("originalKey 不能为空");
                }
                // 通过 XML 显式绑定 PostgresJsonbTypeHandler，避免 LambdaUpdateWrapper 忽略实体 @TableField(typeHandler)
                int updatedAssetRows = fileAssetMapper.markReady(
                        asset.getId(),
                        FileAssetStatus.UPLOAD_CONFIRMED.name(),
                        FileAssetStatus.READY.name(),
                        originalKey,
                        archivedEtag,
                        assetMetadata,
                        now);
                if (updatedAssetRows != 1) {
                    throw new IllegalStateException("切换文件资产为 READY 失败: assetId=" + asset.getId());
                }
            }

            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(committed);
    }

    /**
     * 在短事务中记录文件任务执行失败，按需重排等待重试或标记为终态失败
     * 若任务进入终态失败且属于主任务，同步将关联的文件资产推进为 PROCESSING_FAILED
     *
     * @param job         当前执行中的文件任务
     * @param asset       关联的文件资产
     * @param failureCode 本次失败对应的业务错误码
     * @param requeue     是否重新入队等待重试；false 表示直接标记为终态失败
     * @return 事务成功更新任务状态返回 true；若任务租约已失效或状态不匹配则返回 false
     */
    public boolean recordFailure(FileJob job, FileAsset asset, FileJobCode failureCode, boolean requeue) {
        Objects.requireNonNull(job, "job 不能为空");
        Objects.requireNonNull(failureCode, "failureCode 不能为空");

        Boolean updated = transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            if (requeue) {
                Duration retryDelay = FileJobConstants
                        .retryDelayAfterAttempt(job.getAttemptCount())
                        .orElseThrow(
                                () -> new IllegalStateException("当前尝试次数不可重排: attemptCount=" + job.getAttemptCount()));

                OffsetDateTime nextAvailableAt = now.plus(retryDelay);
                int updatedRows = fileJobMapper.rescheduleRunningForRetry(
                        job.getId(),
                        job.getAttemptCount(),
                        nextAvailableAt,
                        failureCode.code(),
                        now);
                return updatedRows == 1;
            }

            int updatedRows = fileJobMapper.markRunningFailed(
                    job.getId(),
                    job.getAttemptCount(),
                    failureCode.code(),
                    now);
            if (updatedRows != 1) {
                return Boolean.FALSE;
            }

            boolean advancesAssetStatus = FileJobType
                    .findByName(job.getType())
                    .map(FileJobType::isAdvancesAssetStatus)
                    .orElse(false);
            if (asset != null && advancesAssetStatus) {
                fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                        .eq(FileAsset::getId, asset.getId())
                        .eq(FileAsset::getStatus, FileAssetStatus.UPLOAD_CONFIRMED.name())
                        .set(FileAsset::getStatus, FileAssetStatus.PROCESSING_FAILED.name())
                        .set(FileAsset::getUpdatedAt, now));
            }
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(updated);
    }
}
