package com.nep.tsuki.media.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nep.tsuki.common.exceptions.BusinessException;
import com.nep.tsuki.media.config.MediaLifecycleProperties;
import com.nep.tsuki.media.mapper.MediaAssetMapper;
import com.nep.tsuki.media.mapper.MediaVariantMapper;
import com.nep.tsuki.media.model.entity.MediaAsset;
import com.nep.tsuki.media.model.entity.MediaVariant;
import com.nep.tsuki.media.model.enums.MediaStatus;
import com.nep.tsuki.media.model.enums.MediaStorageSpace;
import com.nep.tsuki.media.storage.S3MediaStorageClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaCleanupTask {
    private final MediaAssetMapper mediaAssetMapper;
    private final MediaVariantMapper mediaVariantMapper;
    private final S3MediaStorageClient s3MediaStorageClient;
    private final TransactionTemplate transactionTemplate;
    private final MediaLifecycleProperties mediaLifecycleProperties;

    /**
     * 定时清理已过期的媒体资产
     */
    @Scheduled(fixedDelayString = "${tsuki.media.lifecycle.cleanup-fixed-delay:60s}")
    public void cleanupExpiredAssets() {
        int batchSize = mediaLifecycleProperties.getCleanupBatchSize();
        for (int i = 0; i < batchSize; i++) {
            // 单条资产独立事务执行，避免长事务与单条失败回滚整批
            Boolean processed = transactionTemplate.execute(status -> cleanupOneExpiredAsset());
            if (!Boolean.TRUE.equals(processed)) {
                break;
            }
        }
    }

    /**
     * 在当前事务内认领并清理单条已过期的媒体资产
     */
    private boolean cleanupOneExpiredAsset() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // 跳过已被其他实例锁定的行，认领一条到期资产
        MediaAsset mediaAsset = mediaAssetMapper.selectOneExpiredForUpdateSkipLocked(now);
        if (mediaAsset == null) {
            return false;
        }

        // 查询该资产下的全部衍生规格
        List<MediaVariant> mediaVariants = mediaVariantMapper.selectList(new LambdaQueryWrapper<MediaVariant>()
                .eq(MediaVariant::getAssetId, mediaAsset.getId()));

        try {
            // 先删对象存储文件，全部成功后再删主表（由外键级联清理 media_variants）
            for (MediaVariant variant : mediaVariants) {
                s3MediaStorageClient.deleteObject(MediaStorageSpace.valueOf(variant.getStorageSpace()),
                        variant.getObjectKey());
            }
            mediaAssetMapper.deleteById(mediaAsset.getId());
            log.info("清理过期媒体资源成功, assetId={}, variantCount={}", mediaAsset.getId(), mediaVariants.size());
        } catch (BusinessException ex) {
            // 仅捕获存储层业务异常，避免误吞数据库异常导致在已中止的事务中继续执行 SQL
            OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime retryAt = updatedAt.plus(mediaLifecycleProperties.getCleanupRetryDelay());

            mediaAssetMapper.update(null, new LambdaUpdateWrapper<MediaAsset>()
                    .eq(MediaAsset::getId, mediaAsset.getId())
                    .set(MediaAsset::getStatus, MediaStatus.PURGING.name())
                    .set(MediaAsset::getCleanupAt, retryAt)
                    .set(MediaAsset::getUpdatedAt, updatedAt));
            log.error("清理过期媒体资源失败, 已标记 PURGING 并延后重试: assetId={}, retryAt={}", mediaAsset.getId(), retryAt, ex);
        }
        return true;
    }
}
