package com.nep.tsuki.media.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nep.tsuki.common.api.ApiCode;
import com.nep.tsuki.common.enums.AccountType;
import com.nep.tsuki.common.exceptions.BusinessException;
import com.nep.tsuki.media.config.MediaUploadProperties;
import com.nep.tsuki.media.constants.MediaApiCode;
import com.nep.tsuki.media.constants.MediaRedisConstants;
import com.nep.tsuki.media.mapper.MediaAssetMapper;
import com.nep.tsuki.media.model.dto.IssuedMediaUpload;
import com.nep.tsuki.media.model.dto.PresignedImageUpload;
import com.nep.tsuki.media.model.dto.StagedObjectMetadata;
import com.nep.tsuki.media.model.dto.StagingObjectKey;
import com.nep.tsuki.media.model.entity.MediaAsset;
import com.nep.tsuki.media.model.enums.ImageContentType;
import com.nep.tsuki.media.model.enums.MediaAssetStatus;
import com.nep.tsuki.media.model.enums.MediaKind;
import com.nep.tsuki.media.storage.StagedUploadStorage;
import com.nep.tsuki.media.utils.MediaUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaUploadService {
    private static final long CONFIRM_GRACE_MINUTES = 5L;

    private final MediaAssetMapper mediaAssetMapper;
    private final StagedUploadStorage stagedUploadStorage;
    private final MediaUploadProperties mediaUploadProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> fixedWindowRateLimitScript;

    /**
     * 签发图片直传预签名凭证并创建待上传媒体资产记录
     *
     * @param uploaderAccountId 上传者账号 ID
     * @param accountType       上传者账号类型
     * @param contentType       图片 MIME 类型
     * @param byteSize          图片字节大小
     * @return 包含媒体资产 ID 与直传凭证的结果对象
     */
    public IssuedMediaUpload issueImageUpload(
            long uploaderAccountId,
            AccountType accountType,
            String contentType,
            long byteSize) {
        // 校验调用方传入的基础账号参数
        if (uploaderAccountId <= 0L) {
            throw new IllegalArgumentException("uploaderAccountId 必须大于 0");
        }
        if (accountType == null) {
            throw new IllegalArgumentException("accountType 不能为空");
        }

        // 规范化并校验图片 MIME 类型与文件大小限制
        if (!StringUtils.hasText(contentType)) {
            throw new BusinessException(ApiCode.VALIDATION_FAILED, "图片 Content-Type 不能为空");
        }
        String normalizedContentType = contentType.strip().toLowerCase(Locale.ROOT);
        if (!ImageContentType.isSupported(normalizedContentType)) {
            throw new BusinessException(ApiCode.VALIDATION_FAILED, "不支持的图片 Content-Type: " + normalizedContentType);
        }
        long maxImageBytes = mediaUploadProperties.getMaxImageBytes();
        if (byteSize < 1L || byteSize > maxImageBytes) {
            throw new BusinessException(ApiCode.VALIDATION_FAILED, "图片字节数必须在 1 到 " + maxImageBytes + " 之间");
        }

        // 扣减当前账号在时间窗口内的签发配额
        consumeSignQuota(uploaderAccountId, accountType);

        // 生成按 UTC 日期分区的对象键及对象存储 PUT 预签名凭证
        StagingObjectKey stagingObjectKey = StagingObjectKey.forImage(LocalDate.now(ZoneOffset.UTC), UUID.randomUUID());
        PresignedImageUpload upload = stagedUploadStorage.presignImagePut(
                stagingObjectKey,
                normalizedContentType,
                byteSize);

        // 持久化待确认状态的媒体资产记录
        OffsetDateTime confirmExpiresAt = upload
                .expiresAt()
                .atOffset(ZoneOffset.UTC)
                .plusMinutes(CONFIRM_GRACE_MINUTES);
        MediaAsset mediaAsset = new MediaAsset();
        mediaAsset.setUploaderAccountId(uploaderAccountId);
        mediaAsset.setKind(MediaKind.IMAGE.name());
        mediaAsset.setStatus(MediaAssetStatus.PENDING_UPLOAD.name());
        mediaAsset.setObjectKey(stagingObjectKey.value());
        mediaAsset.setContentType(normalizedContentType);
        mediaAsset.setByteSize(byteSize);
        mediaAsset.setConfirmExpiresAt(confirmExpiresAt);
        mediaAssetMapper.insert(mediaAsset);

        log.info("签发图片直传凭证成功: assetId={}, uploaderAccountId={}, accountType={}, byteSize={}", mediaAsset.getId(),
                uploaderAccountId, accountType, byteSize);
        return new IssuedMediaUpload(mediaAsset.getId(), upload);
    }

    /**
     * 校验暂存区对象元数据并确认图片上传完成
     *
     * @param assetId           媒体资产 ID
     * @param uploaderAccountId 上传者账号 ID
     */
    public void confirmImageUpload(long assetId, long uploaderAccountId) {
        // 校验基础参数合法性
        if (assetId <= 0L) {
            throw new IllegalArgumentException("assetId 必须大于 0");
        }
        if (uploaderAccountId <= 0L) {
            throw new IllegalArgumentException("uploaderAccountId 必须大于 0");
        }

        // 查询归属当前账号的媒体资产，已确认则幂等返回，已过期则拒绝确认
        MediaAsset mediaAsset = mediaAssetMapper.selectOne(new LambdaQueryWrapper<MediaAsset>()
                .eq(MediaAsset::getId, assetId)
                .eq(MediaAsset::getUploaderAccountId, uploaderAccountId));
        if (mediaAsset == null) {
            throw new BusinessException(ApiCode.NOT_FOUND);
        }
        if (mediaAsset.getConfirmedAt() != null) {
            log.debug("同账号重复确认, 幂等跳过: assetId={}, accountId={}", mediaAsset.getId(), uploaderAccountId);
            return;
        }
        OffsetDateTime checkTime = OffsetDateTime.now(ZoneOffset.UTC);
        if (!mediaAsset.getConfirmExpiresAt().isAfter(checkTime)) {
            throw new BusinessException(MediaApiCode.MEDIA_UPLOAD_EXPIRED);
        }

        // 查询对象存储实际元数据，校验文件是否存在以及大小、MIME 类型是否与签发时一致
        Optional<StagedObjectMetadata> metadataOptional = stagedUploadStorage.find(mediaAsset.getObjectKey());
        if (metadataOptional.isEmpty()) {
            throw new BusinessException(MediaApiCode.MEDIA_UPLOAD_OBJECT_MISSING);
        }
        StagedObjectMetadata metadata = metadataOptional.get();
        if (metadata.byteSize() != mediaAsset.getByteSize()
                || !MediaUtils.isSameMediaType(mediaAsset.getContentType(), metadata.contentType())) {
            throw new BusinessException(MediaApiCode.MEDIA_UPLOAD_METADATA_MISMATCH);
        }

        // 基于乐观条件（未确认且未过期）原子更新资产状态与 ETag
        OffsetDateTime confirmedAt = OffsetDateTime.now(ZoneOffset.UTC);
        int updatedRows = mediaAssetMapper.update(null, new LambdaUpdateWrapper<MediaAsset>()
                .eq(MediaAsset::getId, assetId)
                .eq(MediaAsset::getUploaderAccountId, uploaderAccountId)
                .eq(MediaAsset::getStatus, MediaAssetStatus.PENDING_UPLOAD.name())
                .isNull(MediaAsset::getConfirmedAt)
                .gt(MediaAsset::getConfirmExpiresAt, confirmedAt)
                .set(MediaAsset::getStatus, MediaAssetStatus.UPLOAD_CONFIRMED.name())
                .set(MediaAsset::getConfirmedAt, confirmedAt)
                .set(MediaAsset::getEtag, metadata.eTag())
                .set(MediaAsset::getUpdatedAt, confirmedAt));
        if (updatedRows == 1) {
            log.info("确认图片上传成功: assetId={}, uploaderAccountId={}, byteSize={}", assetId, uploaderAccountId,
                    metadata.byteSize());
            return;
        }

        // 并发更新未命中时回查最新状态，区分并发幂等成功、临界过期或异常状态
        MediaAsset latestAsset = mediaAssetMapper.selectOne(new LambdaQueryWrapper<MediaAsset>()
                .eq(MediaAsset::getId, assetId)
                .eq(MediaAsset::getUploaderAccountId, uploaderAccountId));
        if (latestAsset == null) {
            throw new BusinessException(ApiCode.NOT_FOUND);
        }
        if (latestAsset.getConfirmedAt() != null) {
            return;
        }
        if (!latestAsset.getConfirmExpiresAt().isAfter(confirmedAt)) {
            throw new BusinessException(MediaApiCode.MEDIA_UPLOAD_EXPIRED);
        }
        throw new IllegalStateException("媒体资产状态异常, 无法完成确认: assetId=" + assetId + ", status=" + latestAsset.getStatus());
    }

    /**
     * 校验并扣减指定账号在当前时间窗口内的媒体上传签名配额
     *
     * @param uploaderAccountId 上传者账号 ID
     * @param accountType       上传者账号类型
     */
    private void consumeSignQuota(long uploaderAccountId, AccountType accountType) {
        // 拼接按账号类型与账号 ID 隔离的 Redis 限流键
        String rateLimitKey = MediaRedisConstants.UPLOAD_SIGN_KEY_PREFIX
                + accountType.name().toLowerCase(Locale.ROOT) + ":"
                + uploaderAccountId;

        // 按账号角色选择对应的窗口签名配额上限
        long signLimit = accountType == AccountType.ADMIN
                ? mediaUploadProperties.getAdminSignLimit()
                : mediaUploadProperties.getUserSignLimit();

        try {
            // 执行固定窗口限流 Lua 脚本，返回需要等待的剩余毫秒数（0 表示放行）
            Long remainingMillis = stringRedisTemplate.execute(
                    fixedWindowRateLimitScript,
                    List.of(rateLimitKey),
                    String.valueOf(signLimit),
                    String.valueOf(MediaRedisConstants.UPLOAD_SIGN_WINDOW_MILLIS));
            if (remainingMillis == null) {
                log.warn("媒体上传签发限流脚本返回空值, key={}", rateLimitKey);
                throw new BusinessException(MediaApiCode.MEDIA_UPLOAD_LIMIT_UNAVAILABLE);
            }
            // 存在剩余冷却时间说明已触发限流，向上取整转换为秒提示重试
            if (remainingMillis > 0L) {
                long retryAfterSeconds = (remainingMillis + 999L) / 1000L;
                throw new BusinessException(MediaApiCode.MEDIA_UPLOAD_RATE_LIMITED,
                        "上传签名请求过于频繁, 请" + retryAfterSeconds + "秒后重试");
            }
        } catch (DataAccessException ex) {
            // 兜底处理 Redis 网络抖动或脚本执行异常
            log.error("执行媒体上传签发限流脚本失败, key={}", rateLimitKey, ex);
            throw new BusinessException(MediaApiCode.MEDIA_UPLOAD_LIMIT_UNAVAILABLE);
        }
    }

}
