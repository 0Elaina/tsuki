package com.nep.tsuki.media.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nep.tsuki.media.config.MediaJobsProperties;
import com.nep.tsuki.media.constants.MediaJobConstants;
import com.nep.tsuki.media.mapper.MediaAssetMapper;
import com.nep.tsuki.media.mapper.MediaJobMapper;
import com.nep.tsuki.media.mapper.MediaVariantMapper;
import com.nep.tsuki.media.model.entity.MediaAsset;
import com.nep.tsuki.media.model.entity.MediaJob;
import com.nep.tsuki.media.model.entity.MediaVariant;
import com.nep.tsuki.media.model.enums.MediaAssetStatus;
import com.nep.tsuki.media.model.enums.MediaJobType;
import com.nep.tsuki.media.processing.MediaJobCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MediaJobTxService {
    private final MediaJobMapper mediaJobMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final MediaVariantMapper mediaVariantMapper;
    private final MediaJobsProperties mediaJobsProperties;
    private final TransactionTemplate transactionTemplate;

    public record ClaimedMediaJob(MediaJob job, MediaAsset asset) {
    }

    /**
     * 在短事务中原子抢占指定类型的媒体处理任务，并加载其关联的媒体资产
     *
     * @param jobId        待执行的媒体任务主键 ID
     * @param expectedType 期望的任务类型，用于校验类型匹配并计算租约时长
     * @return 抢占成功返回包含任务与资产的包装对象；若任务不存在、类型不符或状态不可抢占则返回 null
     */
    public ClaimedMediaJob claimForExecution(long jobId, MediaJobType expectedType) {
        Objects.requireNonNull(expectedType, "expectedType 不能为空");
        return transactionTemplate.execute(status -> {
            // 使用 UTC 当前时间计算本次执行的租约截止时间
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime leaseUntil = now.plus(mediaJobsProperties.getLeaseFor(expectedType));

            // 原子将任务从 PENDING 推进至 RUNNING 并加租约；抢占失败说明已被其他节点消费或状态已变更
            MediaJob job = mediaJobMapper.claimForExecution(
                    jobId,
                    expectedType.name(),
                    now,
                    leaseUntil);
            if (job == null) {
                return null;
            }

            // 抢占成功后加载关联的媒体资产记录
            MediaAsset asset = mediaAssetMapper.selectById(job.getAssetId());
            return new ClaimedMediaJob(job, asset);
        });
    }

    /**
     * 在短事务中完成媒体任务：标记任务成功、批量落库衍生规格，并在主任务场景下将资产切换为 READY
     *
     * @param job          当前执行中的媒体任务
     * @param asset        关联的媒体资产
     * @param originalKey  归档后的原始对象存储键（仅主任务推进资产状态时必填）
     * @param archivedEtag 归档后的原始对象 ETag
     * @param variants     本次处理生成的媒体规格列表（仅归档原件时可传空列表）
     * @return 事务成功提交返回 true；若任务租约已失效或状态不匹配则返回 false
     */
    public boolean completeSucceeded(
            MediaJob job,
            MediaAsset asset,
            String originalKey,
            String archivedEtag,
            List<MediaVariant> variants) {
        Objects.requireNonNull(job, "job 不能为空");
        Objects.requireNonNull(asset, "asset 不能为空");
        Objects.requireNonNull(variants, "variants 不能为空");

        Boolean committed = transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            // 原子校验租约与尝试次数并将任务推进为 SUCCEEDED；未命中说明租约已失效，直接放弃落库
            int updatedJobRows = mediaJobMapper.markJobSucceeded(
                    job.getId(),
                    job.getAttemptCount(),
                    now);
            if (updatedJobRows != 1) {
                return Boolean.FALSE;
            }

            // 补齐缺失的 assetId 并批量插入衍生规格记录
            if (!variants.isEmpty()) {
                for (MediaVariant variant : variants) {
                    if (variant.getAssetId() == null) {
                        variant.setAssetId(asset.getId());
                    }
                }
                // 不能直接调用 MyBatis-Plus 的 insert(Collection)：同事务前序更新已绑定 SIMPLE 执行器
                // MP 批量方法会切 BATCH 触发 ExecutorType 冲突
                mediaVariantMapper.insertBatch(variants, now);
            }

            // 若当前为主任务，同步更新资产的归档路径、ETag 并推进状态至 READY
            boolean advancesAssetStatus = MediaJobType
                    .findByName(job.getType())
                    .map(MediaJobType::isAdvancesAssetStatus)
                    .orElse(false);
            if (advancesAssetStatus) {
                if (!StringUtils.hasText(originalKey)) {
                    throw new IllegalArgumentException("originalKey 不能为空");
                }
                int updatedAssetRows = mediaAssetMapper.update(null, new LambdaUpdateWrapper<MediaAsset>()
                        .eq(MediaAsset::getId, asset.getId())
                        .eq(MediaAsset::getStatus, MediaAssetStatus.UPLOAD_CONFIRMED.name())
                        .set(MediaAsset::getObjectKey, originalKey)
                        .set(MediaAsset::getEtag, archivedEtag)
                        .set(MediaAsset::getStatus, MediaAssetStatus.READY.name())
                        .set(MediaAsset::getUpdatedAt, now));
                if (updatedAssetRows != 1) {
                    // 资产状态异常时抛异常触发整个短事务回滚，保证任务与资产状态一致
                    throw new IllegalStateException("切换媒体资产为 READY 失败: assetId=" + asset.getId());
                }
            }

            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(committed);
    }

    /**
     * 在短事务中记录媒体任务执行失败，按需重排等待重试或标记为终态失败
     * 若任务进入终态失败且属于主任务，同步将关联的媒体资产推进为 PROCESSING_FAILED
     *
     * @param job         当前执行中的媒体任务
     * @param asset       关联的媒体资产
     * @param failureCode 本次失败对应的业务错误码
     * @param requeue     是否重新入队等待重试；false 表示直接标记为终态失败
     * @return 事务成功更新任务状态返回 true；若任务租约已失效或状态不匹配则返回 false
     */
    public boolean recordFailure(MediaJob job, MediaAsset asset, MediaJobCode failureCode, boolean requeue) {
        Objects.requireNonNull(job, "job 不能为空");
        Objects.requireNonNull(failureCode, "failureCode 不能为空");

        Boolean updated = transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            // 需要重试时，按当前尝试次数计算退避延迟并将任务重新调度为 PENDING
            if (requeue) {
                Duration retryDelay = MediaJobConstants
                        .retryDelayAfterAttempt(
                                job.getAttemptCount())
                        .orElseThrow(
                                () -> new IllegalStateException("当前尝试次数不可重排: attemptCount=" + job.getAttemptCount()));

                OffsetDateTime nextAvailableAt = now.plus(retryDelay);
                int updatedRows = mediaJobMapper.rescheduleRunningForRetry(
                        job.getId(),
                        job.getAttemptCount(),
                        nextAvailableAt,
                        failureCode.code(),
                        now);
                return updatedRows == 1;
            }

            // 终态失败分支：原子校验租约与尝试次数后将任务标记为 FAILED
            int updatedRows = mediaJobMapper.markRunningFailed(
                    job.getId(),
                    job.getAttemptCount(),
                    failureCode.code(),
                    now);
            if (updatedRows != 1) {
                return Boolean.FALSE;
            }

            // 若为主任务且关联资产存在，同步将资产状态从 UPLOAD_CONFIRMED 推进至 PROCESSING_FAILED
            boolean advancesAssetStatus = MediaJobType
                    .findByName(job.getType())
                    .map(MediaJobType::isAdvancesAssetStatus)
                    .orElse(false);
            if (asset != null && advancesAssetStatus) {
                mediaAssetMapper.update(null, new LambdaUpdateWrapper<MediaAsset>()
                        .eq(MediaAsset::getId, asset.getId())
                        .eq(MediaAsset::getStatus, MediaAssetStatus.UPLOAD_CONFIRMED.name())
                        .set(MediaAsset::getStatus, MediaAssetStatus.PROCESSING_FAILED.name())
                        .set(MediaAsset::getUpdatedAt, now));
            }
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(updated);
    }

    /**
     * 检查指定的原始对象键或任意衍生规格对象键是否已被数据库正式引用
     * 常用于事务提交结果不明确时，核对已上传的对象存储文件是否可安全清理
     *
     * @param assetId     媒体资产主键 ID
     * @param originalKey 待核对的原始归档对象键（可为空）
     * @param variantKeys 待核对的衍生规格对象键集合（可为空）
     * @return 只要任意一个对象键已在资产表或规格表中存在引用则返回 true，否则返回 false
     */
    public boolean isAnyObjectReferenced(
            long assetId,
            String originalKey,
            Collection<String> variantKeys) {
        // 若传入了有效的原始对象键，优先核对资产表当前是否已指向该键
        if (StringUtils.hasText(originalKey)) {
            MediaAsset currentAsset = mediaAssetMapper.selectById(assetId);
            if (currentAsset != null && originalKey.equals(currentAsset.getObjectKey())) {
                return true;
            }
        }

        // 过滤空白与重复的规格对象键，无有效键时直接返回未引用
        List<String> validVariantKeys = variantKeys == null
                ? List.of()
                : variantKeys.stream()
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList();
        if (validVariantKeys.isEmpty()) {
            return false;
        }

        // 批量查询规格表中是否存在对上述对象键的引用记录
        Long variantCount = mediaVariantMapper.selectCount(new LambdaQueryWrapper<MediaVariant>()
                .eq(MediaVariant::getAssetId, assetId)
                .in(MediaVariant::getObjectKey, validVariantKeys));
        return variantCount != null && variantCount > 0L;
    }
}
