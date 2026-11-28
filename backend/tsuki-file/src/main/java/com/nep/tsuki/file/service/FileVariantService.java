package com.nep.tsuki.file.service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nep.tsuki.common.api.ApiCode;
import com.nep.tsuki.common.exceptions.BusinessException;
import com.nep.tsuki.file.constants.FileApiCode;
import com.nep.tsuki.file.mapper.FileAssetMapper;
import com.nep.tsuki.file.mapper.FileJobMapper;
import com.nep.tsuki.file.mapper.FileVariantMapper;
import com.nep.tsuki.file.model.entity.FileAsset;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.entity.FileVariant;
import com.nep.tsuki.file.model.enums.FileAssetStatus;
import com.nep.tsuki.file.model.enums.FileJobStatus;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.FileKind;
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.model.enums.FileVariantStatus;
import com.nep.tsuki.file.model.enums.ImageVariantPreset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileVariantService {
    private static final List<String> GENERATE_VARIANT_TYPES = List.of(FileJobType.GENERATE_VARIANT.name());

    private final FileAssetMapper fileAssetMapper;
    private final FileVariantMapper fileVariantMapper;
    private final FileJobMapper fileJobMapper;
    private final FileAccessService fileAccessService;

    /**
     * 按主键查询衍生物记录
     */
    public FileVariant getVariant(long variantId) {
        validatePositive(variantId, "variantId");
        FileVariant variant = fileVariantMapper.selectById(variantId);
        if (variant == null) {
            throw new BusinessException(FileApiCode.FILE_VARIANT_NOT_FOUND);
        }
        return variant;
    }

    /**
     * 业务完成鉴权后解析已就绪衍生物的访问地址（撤回或资产删除立即停止签发）
     */
    public URI resolveVariantUrl(long variantId) {
        FileVariant variant = getVariant(variantId);
        if (!FileVariantStatus.READY.name().equals(variant.getStatus())
                || !StringUtils.hasText(variant.getObjectKey())) {
            throw new BusinessException(FileApiCode.FILE_VARIANT_NOT_READY);
        }
        FileAsset asset = fileAssetMapper.selectById(variant.getAssetId());
        if (asset == null || !FileAssetStatus.READY.name().equals(asset.getStatus())) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_READY);
        }
        return fileAccessService.resolveUrl(FileStorageScope.valueOf(variant.getStorageScope()), variant.getObjectKey());
    }

    /**
     * 按规格编码最新版本申请衍生物（要求资产已就绪且已绑定）
     */
    @Transactional
    public FileVariant requestVariant(long assetId, String code, FileStorageScope storageScope) {
        validatePositive(assetId, "assetId");
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ApiCode.VALIDATION_FAILED, "衍生物规格编码不能为空");
        }
        FileAsset asset = fileAssetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_FOUND);
        }
        int latestRevision = resolveLatestPresetRevision(FileKind.valueOf(asset.getKind()), code.strip());
        return requestVariant(assetId, code.strip(), latestRevision, storageScope);
    }

    /**
     * 按资产 ID、规格编码、预设版本与存储范围申请衍生物：未撤回记录存在时幂等返回既有记录，撤回后重新申请生成新记录
     */
    @Transactional
    public FileVariant requestVariant(long assetId, String code, int presetRevision, FileStorageScope storageScope) {
        validatePositive(assetId, "assetId");
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ApiCode.VALIDATION_FAILED, "衍生物规格编码不能为空");
        }
        if (presetRevision <= 0) {
            throw new BusinessException(ApiCode.VALIDATION_FAILED, "衍生物规格版本必须大于 0");
        }
        Objects.requireNonNull(storageScope, "storageScope 不能为空");

        fileAccessService.ensureScopeWritable(storageScope);

        // 锁定父资产行，保证同一资产下的衍生物申请串行并严格遵循 Asset -> Variant -> Job 锁序
        FileAsset asset = fileAssetMapper.selectByIdForUpdate(assetId);
        if (asset == null) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_FOUND);
        }
        if (!FileAssetStatus.READY.name().equals(asset.getStatus())) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_READY);
        }
        if (asset.getOwnerType() == null || asset.getOwnerId() == null) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_BOUND);
        }

        String normalizedCode = code.strip();
        validatePresetRegistered(FileKind.valueOf(asset.getKind()), normalizedCode, presetRevision);

        FileVariant existing = fileVariantMapper.selectOne(new LambdaQueryWrapper<FileVariant>()
                .eq(FileVariant::getAssetId, assetId)
                .eq(FileVariant::getCode, normalizedCode)
                .eq(FileVariant::getPresetRevision, presetRevision)
                .eq(FileVariant::getStorageScope, storageScope.name())
                .ne(FileVariant::getStatus, FileVariantStatus.WITHDRAWN.name()));
        if (existing != null) {
            return existing;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        FileVariant variant = new FileVariant();
        variant.setAssetId(assetId);
        variant.setCode(normalizedCode);
        variant.setPresetRevision(presetRevision);
        variant.setStatus(FileVariantStatus.PENDING.name());
        variant.setStorageScope(storageScope.name());
        fileVariantMapper.insert(variant);

        enqueueGenerateVariantJob(assetId, variant.getId(), now);
        log.info("申请文件衍生物成功: variantId={}, assetId={}, code={}, revision={}, scope={}",
                variant.getId(), assetId, normalizedCode, presetRevision, storageScope);
        return variant;
    }

    /**
     * 显式重试失败的衍生物：复用原 variantId，重置为 PENDING 并重新入队 GENERATE_VARIANT
     */
    @Transactional
    public FileVariant retryVariant(long variantId) {
        validatePositive(variantId, "variantId");
        FileVariant snapshot = fileVariantMapper.selectById(variantId);
        if (snapshot == null) {
            throw new BusinessException(FileApiCode.FILE_VARIANT_NOT_FOUND);
        }

        FileAsset asset = fileAssetMapper.selectByIdForUpdate(snapshot.getAssetId());
        FileVariant variant = fileVariantMapper.selectByIdForUpdate(variantId);
        if (asset == null || !FileAssetStatus.READY.name().equals(asset.getStatus())) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_READY);
        }
        if (variant == null) {
            throw new BusinessException(FileApiCode.FILE_VARIANT_NOT_FOUND);
        }
        if (!FileVariantStatus.FAILED.name().equals(variant.getStatus())) {
            throw new BusinessException(FileApiCode.FILE_VARIANT_NOT_RETRYABLE);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int updatedRows = fileVariantMapper.update(null, new LambdaUpdateWrapper<FileVariant>()
                .eq(FileVariant::getId, variantId)
                .eq(FileVariant::getStatus, FileVariantStatus.FAILED.name())
                .set(FileVariant::getStatus, FileVariantStatus.PENDING.name())
                .set(FileVariant::getFailureCode, null)
                .set(FileVariant::getFailureMessage, null)
                .set(FileVariant::getUpdatedAt, now));
        if (updatedRows != 1) {
            throw new BusinessException(FileApiCode.FILE_VARIANT_NOT_RETRYABLE);
        }

        enqueueGenerateVariantJob(asset.getId(), variantId, now);
        variant.setStatus(FileVariantStatus.PENDING.name());
        variant.setFailureCode(null);
        variant.setFailureMessage(null);
        variant.setUpdatedAt(now);
        log.info("重试失败文件衍生物成功: variantId={}, assetId={}", variantId, asset.getId());
        return variant;
    }

    /**
     * 撤回衍生物：同事务立即停发、取消未领取生成任务并登记 CLEANUP_VARIANT（幂等）
     */
    @Transactional
    public void withdrawVariant(long variantId) {
        validatePositive(variantId, "variantId");
        FileVariant snapshot = fileVariantMapper.selectById(variantId);
        if (snapshot == null) {
            throw new BusinessException(FileApiCode.FILE_VARIANT_NOT_FOUND);
        }

        fileAssetMapper.selectByIdForUpdate(snapshot.getAssetId());
        FileVariant variant = fileVariantMapper.selectByIdForUpdate(variantId);
        if (variant == null) {
            throw new BusinessException(FileApiCode.FILE_VARIANT_NOT_FOUND);
        }
        if (FileVariantStatus.WITHDRAWN.name().equals(variant.getStatus())) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        fileVariantMapper.update(null, new LambdaUpdateWrapper<FileVariant>()
                .eq(FileVariant::getId, variantId)
                .ne(FileVariant::getStatus, FileVariantStatus.WITHDRAWN.name())
                .set(FileVariant::getStatus, FileVariantStatus.WITHDRAWN.name())
                .set(FileVariant::getWithdrawnAt, now)
                .set(FileVariant::getUpdatedAt, now));

        fileJobMapper.cancelPendingByVariant(variantId, GENERATE_VARIANT_TYPES, now);

        FileJob cleanupJob = new FileJob();
        cleanupJob.setAssetId(variant.getAssetId());
        cleanupJob.setVariantId(variantId);
        cleanupJob.setType(FileJobType.CLEANUP_VARIANT.name());
        cleanupJob.setStatus(FileJobStatus.PENDING.name());
        cleanupJob.setAttemptCount(0);
        cleanupJob.setAvailableAt(now);
        cleanupJob.setDispatchAfter(now);
        cleanupJob.setDedupKey(variant.getAssetId() + ":" + FileJobType.CLEANUP_VARIANT.name() + ":" + variantId);
        fileJobMapper.insertIfNotActive(cleanupJob, now);

        log.info("撤回文件衍生物并登记清理成功: variantId={}, assetId={}", variantId, variant.getAssetId());
    }

    private void enqueueGenerateVariantJob(long assetId, long variantId, OffsetDateTime now) {
        FileJob job = new FileJob();
        job.setAssetId(assetId);
        job.setVariantId(variantId);
        job.setType(FileJobType.GENERATE_VARIANT.name());
        job.setStatus(FileJobStatus.PENDING.name());
        job.setAttemptCount(0);
        job.setAvailableAt(now);
        job.setDispatchAfter(now);
        job.setDedupKey(assetId + ":" + FileJobType.GENERATE_VARIANT.name() + ":" + variantId);
        fileJobMapper.insertIfNotActive(job, now);
    }

    private int resolveLatestPresetRevision(FileKind kind, String code) {
        return switch (kind) {
            case IMAGE -> ImageVariantPreset.findLatest(code)
                    .map(ImageVariantPreset::getRevision)
                    .orElseThrow(() -> new BusinessException(ApiCode.VALIDATION_FAILED, "未注册的图片衍生物规格: " + code));
        };
    }

    private void validatePresetRegistered(FileKind kind, String code, int presetRevision) {
        switch (kind) {
            case IMAGE -> ImageVariantPreset.find(code, presetRevision)
                    .orElseThrow(() -> new BusinessException(ApiCode.VALIDATION_FAILED,
                            "未注册的图片衍生物规格与版本: code=" + code + ", revision=" + presetRevision));
        }
    }

    private void validatePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " 必须大于 0: " + value);
        }
    }
}
