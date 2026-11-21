package com.nep.tsuki.media.image.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nep.tsuki.common.exceptions.BusinessException;
import com.nep.tsuki.media.config.MediaImageProperties;
import com.nep.tsuki.media.config.MediaLifecycleProperties;
import com.nep.tsuki.media.constants.MediaApiCode;
import com.nep.tsuki.media.constants.MediaConstants;
import com.nep.tsuki.media.image.model.vo.BoundImage;
import com.nep.tsuki.media.image.model.vo.MediaImageUploadResponse;
import com.nep.tsuki.media.image.processing.ImageProcessor;
import com.nep.tsuki.media.image.processing.ProcessedImage;
import com.nep.tsuki.media.image.service.MediaImageService;
import com.nep.tsuki.media.mapper.MediaAssetMapper;
import com.nep.tsuki.media.mapper.MediaVariantMapper;
import com.nep.tsuki.media.model.entity.MediaAsset;
import com.nep.tsuki.media.model.entity.MediaVariant;
import com.nep.tsuki.media.model.enums.MediaKind;
import com.nep.tsuki.media.model.enums.MediaStatus;
import com.nep.tsuki.media.model.enums.MediaStorageSpace;
import com.nep.tsuki.media.model.enums.MediaVariantCode;
import com.nep.tsuki.media.storage.S3MediaStorageClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaImageServiceImpl implements MediaImageService {
    private final ImageProcessor imageProcessor;
    private final MediaAssetMapper mediaAssetMapper;
    private final MediaVariantMapper mediaVariantMapper;
    private final S3MediaStorageClient s3MediaStorageClient;
    private final TransactionTemplate transactionTemplate;
    private final MediaLifecycleProperties mediaLifecycleProperties;
    private final MediaImageProperties mediaImageProperties;

    /**
     * 上传媒体图片文件
     *
     * @param file 待上传的图片文件
     * @return 图片上传结果响应
     */
    @Override
    public MediaImageUploadResponse upload(MultipartFile file) {
        // 事务外校验并重编码图片，避免长耗时操作占用数据库连接
        ProcessedImage processedImage = imageProcessor.process(file);

        // 预创建 UPLOADING 状态的资产与基础规格记录，供超时清理任务兜底
        MediaVariant mediaVariant = transactionTemplate.execute(status -> {
            OffsetDateTime uploadingCleanupAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .plus(mediaLifecycleProperties.getUploadingTtl());

            MediaAsset mediaAsset = new MediaAsset();
            mediaAsset.setKind(MediaKind.IMAGE.name());
            mediaAsset.setStatus(MediaStatus.UPLOADING.name());
            mediaAsset.setCleanupAt(uploadingCleanupAt);
            mediaAssetMapper.insert(mediaAsset);

            if (mediaAsset.getId() == null) {
                throw new IllegalStateException("创建上传中的媒体资源失败");
            }

            // 生成对象键：tsuki/images/{assetId}/base-{uuid}.{ext}
            String objectKey = mediaImageProperties.getKeyPrefix()
                    + mediaAsset.getId() + "/"
                    + MediaConstants.BASE_IMAGE_PREFIX + UUID.randomUUID() + "." + processedImage.extension();

            MediaVariant variant = new MediaVariant();
            variant.setAssetId(mediaAsset.getId());
            variant.setCode(MediaVariantCode.BASE.name());
            variant.setStorageSpace(MediaStorageSpace.PUBLIC_MEDIA.name());
            variant.setObjectKey(objectKey);
            variant.setContentType(processedImage.contentType());
            variant.setByteSize((long) processedImage.bytes().length);
            variant.setWidth(processedImage.width());
            variant.setHeight(processedImage.height());
            mediaVariantMapper.insert(variant);
            return variant;
        });

        if (mediaVariant == null) {
            throw new IllegalStateException("创建上传中的媒体资源失败");
        }

        // 加行锁校验状态，上传对象存储并流转为 AVAILABLE
        OffsetDateTime expiresAt = transactionTemplate.execute(status -> {
            MediaAsset lockedAsset = mediaAssetMapper.selectByIdForUpdate(mediaVariant.getAssetId());
            if (lockedAsset == null || !MediaStatus.UPLOADING.name().equals(lockedAsset.getStatus())) {
                throw new IllegalStateException("媒体资源状态异常, assetId=" + mediaVariant.getAssetId());
            }

            s3MediaStorageClient.putObject(
                    MediaStorageSpace.PUBLIC_MEDIA,
                    mediaVariant.getObjectKey(),
                    processedImage.bytes(),
                    processedImage.contentType());

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime availableUntil = now.plus(mediaLifecycleProperties.getAvailableTtl());

            // CAS 更新状态并刷新过期清理时间
            int updated = mediaAssetMapper.update(null, new LambdaUpdateWrapper<MediaAsset>()
                    .eq(MediaAsset::getId, mediaVariant.getAssetId())
                    .eq(MediaAsset::getStatus, MediaStatus.UPLOADING.name())
                    .set(MediaAsset::getStatus, MediaStatus.AVAILABLE.name())
                    .set(MediaAsset::getCleanupAt, availableUntil)
                    .set(MediaAsset::getUpdatedAt, now));

            if (updated != 1) {
                throw new IllegalStateException("更新媒体资源为可用状态失败, assetId=" + mediaVariant.getAssetId());
            }
            return availableUntil;
        });

        if (expiresAt == null) {
            throw new IllegalStateException("更新媒体为可用状态失败, assetId=" + mediaVariant.getAssetId());
        }

        // 组装公开访问地址并返回
        String url = s3MediaStorageClient.buildPublicUrl(MediaStorageSpace.PUBLIC_MEDIA, mediaVariant.getObjectKey());
        log.info("基础图片上传成功, assetId={}, contentType={}, byteSize={}", mediaVariant.getAssetId(),
                processedImage.contentType(), processedImage.bytes().length);

        return MediaImageUploadResponse.builder()
                .assetId(String.valueOf(mediaVariant.getAssetId()))
                .url(url)
                .contentType(processedImage.contentType())
                .width(processedImage.width())
                .height(processedImage.height())
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * 绑定可用的图片媒体资产
     *
     * @param assetId 媒体资产 ID
     * @return 绑定后的图片信息
     */
    @Override
    public BoundImage bindImage(long assetId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // CAS 校验并流转状态：仅允许未过期的 AVAILABLE 图片转为 BOUND，同时清空 cleanupAt 防止被定时回收
        int updated = mediaAssetMapper.update(null, new LambdaUpdateWrapper<MediaAsset>()
                .eq(MediaAsset::getId, assetId)
                .eq(MediaAsset::getKind, MediaKind.IMAGE.name())
                .eq(MediaAsset::getStatus, MediaStatus.AVAILABLE.name())
                .isNotNull(MediaAsset::getCleanupAt)
                .gt(MediaAsset::getCleanupAt, now)
                .set(MediaAsset::getStatus, MediaStatus.BOUND.name())
                .set(MediaAsset::getCleanupAt, null)
                .set(MediaAsset::getUpdatedAt, now));
        if (updated != 1) {
            throw new BusinessException(MediaApiCode.MEDIA_ASSET_NOT_BINDABLE);
        }

        // 查询对应的基础图片规格记录
        MediaVariant mediaVariant = mediaVariantMapper.selectOne(new LambdaQueryWrapper<MediaVariant>()
                .eq(MediaVariant::getAssetId, assetId)
                .eq(MediaVariant::getCode, MediaVariantCode.BASE.name()));
        if (mediaVariant == null) {
            throw new BusinessException(MediaApiCode.MEDIA_ASSET_NOT_BINDABLE);
        }

        // 组装相对路径与公开访问地址
        String relativePath = "/" + mediaVariant.getObjectKey();
        String url = s3MediaStorageClient.buildPublicUrl(
                MediaStorageSpace.valueOf(mediaVariant.getStorageSpace()),
                mediaVariant.getObjectKey());
        return new BoundImage(assetId, relativePath, url);
    }

    /**
     * 构建图片的公开访问 URL
     * 
     * @param relativePath 图片的相对路径
     * @return 图片的公开访问 URL
     */
    @Override
    public String buildPublicUrl(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        return s3MediaStorageClient.buildPublicUrl(MediaStorageSpace.PUBLIC_MEDIA, relativePath);
    }
}
