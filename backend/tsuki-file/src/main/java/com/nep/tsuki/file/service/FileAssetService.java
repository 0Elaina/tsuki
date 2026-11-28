package com.nep.tsuki.file.service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.model.enums.FileVariantStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileAssetService {
    private static final int MAX_OWNER_TYPE_LENGTH = 64;
    private static final List<String> CANCELLABLE_TYPES_ON_ASSET_DELETE = List.of(
            FileJobType.VALIDATE_UPLOAD.name(),
            FileJobType.GENERATE_VARIANT.name(),
            FileJobType.CLEANUP_VARIANT.name());

    private final FileAssetMapper fileAssetMapper;
    private final FileVariantMapper fileVariantMapper;
    private final FileJobMapper fileJobMapper;
    private final FileAccessService fileAccessService;

    /**
     * 按主键查询文件资产
     */
    public FileAsset getAsset(long assetId) {
        validatePositive(assetId, "assetId");
        FileAsset asset = fileAssetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_FOUND);
        }
        return asset;
    }

    /**
     * 未绑定时供上传者预览私有原件（签发 5 分钟 GET 预签名地址）
     */
    public URI resolvePreviewUrl(long assetId, long uploaderAccountId) {
        validatePositive(assetId, "assetId");
        validatePositive(uploaderAccountId, "uploaderAccountId");

        FileAsset asset = fileAssetMapper.selectOne(new LambdaQueryWrapper<FileAsset>()
                .eq(FileAsset::getId, assetId)
                .eq(FileAsset::getUploaderAccountId, uploaderAccountId));
        if (asset == null) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_FOUND);
        }
        if (!FileAssetStatus.READY.name().equals(asset.getStatus()) || !StringUtils.hasText(asset.getObjectKey())) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_READY);
        }
        if (asset.getOwnerType() != null) {
            throw new BusinessException(FileApiCode.FILE_ASSET_OWNER_CONFLICT, "文件资产已绑定业务记录，请通过业务入口访问");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (asset.getUnboundExpiresAt() != null && !asset.getUnboundExpiresAt().isAfter(now)) {
            throw new BusinessException(FileApiCode.FILE_ASSET_EXPIRED);
        }
        return fileAccessService.resolveUrl(FileStorageScope.valueOf(asset.getStorageScope()), asset.getObjectKey());
    }

    /**
     * 业务完成鉴权后解析已就绪原件的私有访问地址（5 分钟 GET 预签名地址）
     */
    public URI resolveOriginalUrl(long assetId) {
        FileAsset asset = getAsset(assetId);
        if (!FileAssetStatus.READY.name().equals(asset.getStatus()) || !StringUtils.hasText(asset.getObjectKey())) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_READY);
        }
        return fileAccessService.resolveUrl(FileStorageScope.valueOf(asset.getStorageScope()), asset.getObjectKey());
    }

    /**
     * 将已就绪的文件资产绑定到指定业务记录（强校验上传者账号，同事务提交，同一记录重复绑定幂等）
     */
    @Transactional
    public FileAsset bindAsset(long assetId, String ownerType, long ownerId, long expectedUploaderAccountId) {
        validatePositive(expectedUploaderAccountId, "expectedUploaderAccountId");
        return bindAssetInternal(assetId, ownerType, ownerId, Long.valueOf(expectedUploaderAccountId));
    }

    /**
     * 将已就绪的文件资产绑定到指定业务记录（不限制上传者，适用于管理端或内部系统流程）
     */
    @Transactional
    public FileAsset bindAsset(long assetId, String ownerType, long ownerId) {
        return bindAssetInternal(assetId, ownerType, ownerId, null);
    }

    /**
     * 业务更换文件：同事务绑定新资产（强校验上传者），并撤回旧资产衍生物与登记旧资产清理
     */
    @Transactional
    public FileAsset replaceOwnerAsset(
            Long oldAssetId,
            long newAssetId,
            String ownerType,
            long ownerId,
            long expectedUploaderAccountId) {
        validatePositive(expectedUploaderAccountId, "expectedUploaderAccountId");
        return replaceOwnerAssetInternal(oldAssetId, newAssetId, ownerType, ownerId,
                Long.valueOf(expectedUploaderAccountId));
    }

    /**
     * 业务更换文件（不限制新资产上传者）
     */
    @Transactional
    public FileAsset replaceOwnerAsset(Long oldAssetId, long newAssetId, String ownerType, long ownerId) {
        return replaceOwnerAssetInternal(oldAssetId, newAssetId, ownerType, ownerId, null);
    }

    /**
     * 业务解绑并删除指定归属的文件资产：同事务停发、撤回衍生物、取消待执行任务并登记 CLEANUP_ASSET
     */
    @Transactional
    public void deleteAsset(long assetId, String ownerType, long ownerId) {
        validatePositive(assetId, "assetId");
        String normalizedOwnerType = normalizeOwnerType(ownerType);
        validatePositive(ownerId, "ownerId");

        FileAsset asset = fileAssetMapper.selectByIdForUpdate(assetId);
        if (asset == null) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_FOUND);
        }
        if (FileAssetStatus.DELETING.name().equals(asset.getStatus())
                || FileAssetStatus.DELETED.name().equals(asset.getStatus())) {
            return;
        }
        if (!normalizedOwnerType.equals(asset.getOwnerType()) || !Long.valueOf(ownerId).equals(asset.getOwnerId())) {
            throw new BusinessException(FileApiCode.FILE_ASSET_OWNER_CONFLICT, "文件资产不属于当前业务记录");
        }
        markAssetDeletingInTx(asset, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * 管理或系统流程直接标记资产删除并登记清理任务
     */
    @Transactional
    public void deleteAsset(long assetId) {
        validatePositive(assetId, "assetId");
        FileAsset asset = fileAssetMapper.selectByIdForUpdate(assetId);
        if (asset == null) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_FOUND);
        }
        markAssetDeletingInTx(asset, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * 巡检回收单个到期且未绑定的 READY 资产：与绑定锁同一资产行并重查状态，同事务置为 DELETING 并登记 CLEANUP_ASSET
     */
    @Transactional
    public boolean reclaimExpiredUnboundAsset(long assetId) {
        FileAsset asset = fileAssetMapper.selectByIdForUpdate(assetId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (asset == null
                || !FileAssetStatus.READY.name().equals(asset.getStatus())
                || asset.getOwnerType() != null
                || asset.getUnboundExpiresAt() == null
                || asset.getUnboundExpiresAt().isAfter(now)) {
            return false;
        }
        markAssetDeletingInTx(asset, now);
        return true;
    }

    private FileAsset bindAssetInternal(
            long assetId,
            String ownerType,
            long ownerId,
            Long expectedUploaderAccountId) {
        validatePositive(assetId, "assetId");
        String normalizedOwnerType = normalizeOwnerType(ownerType);
        validatePositive(ownerId, "ownerId");

        FileAsset asset = fileAssetMapper.selectByIdForUpdate(assetId);
        return bindLockedAsset(asset, normalizedOwnerType, ownerId, expectedUploaderAccountId,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private FileAsset replaceOwnerAssetInternal(
            Long oldAssetId,
            long newAssetId,
            String ownerType,
            long ownerId,
            Long expectedUploaderAccountId) {
        validatePositive(newAssetId, "newAssetId");
        String normalizedOwnerType = normalizeOwnerType(ownerType);
        validatePositive(ownerId, "ownerId");

        if (oldAssetId == null || oldAssetId.longValue() == newAssetId) {
            return bindAssetInternal(newAssetId, normalizedOwnerType, ownerId, expectedUploaderAccountId);
        }
        validatePositive(oldAssetId, "oldAssetId");

        // 按主键升序锁定两个资产行，避免并发互换产生死锁
        long firstId = Math.min(oldAssetId, newAssetId);
        long secondId = Math.max(oldAssetId, newAssetId);
        FileAsset first = fileAssetMapper.selectByIdForUpdate(firstId);
        FileAsset second = fileAssetMapper.selectByIdForUpdate(secondId);

        FileAsset oldAsset = oldAssetId.longValue() == firstId ? first : second;
        FileAsset newAsset = newAssetId == firstId ? first : second;

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        FileAsset boundNewAsset = bindLockedAsset(newAsset, normalizedOwnerType, ownerId,
                expectedUploaderAccountId, now);

        if (oldAsset != null
                && !FileAssetStatus.DELETING.name().equals(oldAsset.getStatus())
                && !FileAssetStatus.DELETED.name().equals(oldAsset.getStatus())) {
            if (!normalizedOwnerType.equals(oldAsset.getOwnerType())
                    || !Long.valueOf(ownerId).equals(oldAsset.getOwnerId())) {
                throw new BusinessException(FileApiCode.FILE_ASSET_OWNER_CONFLICT, "待替换的旧文件资产不属于当前业务记录");
            }
            markAssetDeletingInTx(oldAsset, now);
        }

        return boundNewAsset;
    }

    private FileAsset bindLockedAsset(
            FileAsset asset,
            String ownerType,
            long ownerId,
            Long expectedUploaderAccountId,
            OffsetDateTime now) {
        if (asset == null) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_FOUND);
        }
        if (FileAssetStatus.EXPIRED.name().equals(asset.getStatus())) {
            throw new BusinessException(FileApiCode.FILE_ASSET_EXPIRED);
        }
        if (!FileAssetStatus.READY.name().equals(asset.getStatus())) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_READY);
        }

        if (asset.getOwnerType() != null) {
            if (ownerType.equals(asset.getOwnerType()) && Long.valueOf(ownerId).equals(asset.getOwnerId())) {
                return asset;
            }
            throw new BusinessException(FileApiCode.FILE_ASSET_OWNER_CONFLICT);
        }

        if (expectedUploaderAccountId != null && !expectedUploaderAccountId.equals(asset.getUploaderAccountId())) {
            throw new BusinessException(ApiCode.ACCESS_DENIED, "无权绑定他人上传的文件资产");
        }
        if (asset.getUnboundExpiresAt() != null && !asset.getUnboundExpiresAt().isAfter(now)) {
            throw new BusinessException(FileApiCode.FILE_ASSET_EXPIRED);
        }

        int updatedRows = fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                .eq(FileAsset::getId, asset.getId())
                .eq(FileAsset::getStatus, FileAssetStatus.READY.name())
                .isNull(FileAsset::getOwnerType)
                .set(FileAsset::getOwnerType, ownerType)
                .set(FileAsset::getOwnerId, ownerId)
                .set(FileAsset::getBoundAt, now)
                .set(FileAsset::getUnboundExpiresAt, null)
                .set(FileAsset::getUpdatedAt, now));
        if (updatedRows != 1) {
            throw new IllegalStateException("绑定文件资产失败: assetId=" + asset.getId());
        }

        asset.setOwnerType(ownerType);
        asset.setOwnerId(ownerId);
        asset.setBoundAt(now);
        asset.setUnboundExpiresAt(null);
        asset.setUpdatedAt(now);
        log.info("绑定文件资产成功: assetId={}, ownerType={}, ownerId={}", asset.getId(), ownerType, ownerId);
        return asset;
    }

    private void markAssetDeletingInTx(FileAsset asset, OffsetDateTime now) {
        if (FileAssetStatus.DELETING.name().equals(asset.getStatus())
                || FileAssetStatus.DELETED.name().equals(asset.getStatus())) {
            return;
        }

        // 遵循 Asset -> Variant -> Job 锁序：先按主键升序锁定并撤回该资产下所有未撤回衍生物
        List<FileVariant> variants = fileVariantMapper.selectByAssetIdForUpdate(asset.getId());
        for (FileVariant variant : variants) {
            if (!FileVariantStatus.WITHDRAWN.name().equals(variant.getStatus())) {
                fileVariantMapper.update(null, new LambdaUpdateWrapper<FileVariant>()
                        .eq(FileVariant::getId, variant.getId())
                        .ne(FileVariant::getStatus, FileVariantStatus.WITHDRAWN.name())
                        .set(FileVariant::getStatus, FileVariantStatus.WITHDRAWN.name())
                        .set(FileVariant::getWithdrawnAt, now)
                        .set(FileVariant::getUpdatedAt, now));
            }
        }

        // 取消该资产下所有尚未领取的校验、生成与单衍生物清理任务（RUNNING 任务保留租约等待自行收敛为 CANCELLED）
        fileJobMapper.cancelPendingByAsset(asset.getId(), CANCELLABLE_TYPES_ON_ASSET_DELETE, now);

        fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                .eq(FileAsset::getId, asset.getId())
                .notIn(FileAsset::getStatus, FileAssetStatus.DELETING.name(), FileAssetStatus.DELETED.name())
                .set(FileAsset::getStatus, FileAssetStatus.DELETING.name())
                .set(FileAsset::getUnboundExpiresAt, null)
                .set(FileAsset::getUpdatedAt, now));

        FileJob cleanupJob = new FileJob();
        cleanupJob.setAssetId(asset.getId());
        cleanupJob.setType(FileJobType.CLEANUP_ASSET.name());
        cleanupJob.setStatus(FileJobStatus.PENDING.name());
        cleanupJob.setAttemptCount(0);
        cleanupJob.setAvailableAt(now);
        cleanupJob.setDispatchAfter(now);
        cleanupJob.setDedupKey(asset.getId() + ":" + FileJobType.CLEANUP_ASSET.name());
        fileJobMapper.insertIfNotActive(cleanupJob, now);

        log.info("登记文件资产清理成功: assetId={}, variantCount={}", asset.getId(), variants.size());
    }

    private String normalizeOwnerType(String ownerType) {
        if (!StringUtils.hasText(ownerType)) {
            throw new IllegalArgumentException("ownerType 不能为空");
        }
        String normalized = ownerType.strip();
        if (normalized.length() > MAX_OWNER_TYPE_LENGTH) {
            throw new IllegalArgumentException("ownerType 长度不能超过 " + MAX_OWNER_TYPE_LENGTH);
        }
        return normalized;
    }

    private void validatePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " 必须大于 0: " + value);
        }
    }
}
