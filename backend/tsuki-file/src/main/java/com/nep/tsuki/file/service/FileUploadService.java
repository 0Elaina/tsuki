package com.nep.tsuki.file.service;

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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nep.tsuki.common.api.ApiCode;
import com.nep.tsuki.common.enums.AccountType;
import com.nep.tsuki.common.exceptions.BusinessException;
import com.nep.tsuki.file.config.FileUploadProperties;
import com.nep.tsuki.file.constants.FileApiCode;
import com.nep.tsuki.file.constants.FileRedisConstants;
import com.nep.tsuki.file.mapper.FileAssetMapper;
import com.nep.tsuki.file.mapper.FileJobMapper;
import com.nep.tsuki.file.model.dto.IssuedFileUpload;
import com.nep.tsuki.file.model.dto.PresignedUpload;
import com.nep.tsuki.file.model.dto.StagedObjectMetadata;
import com.nep.tsuki.file.model.entity.FileAsset;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.enums.FileAssetStatus;
import com.nep.tsuki.file.model.enums.FileJobStatus;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.FileKind;
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.model.enums.ImageContentType;
import com.nep.tsuki.file.storage.StagedUploadStorage;
import com.nep.tsuki.file.utils.FileObjectPathUtils;
import com.nep.tsuki.file.utils.FileUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件直传与上传确认服务，负责签发暂存区预签名 PUT 凭证、核对暂存对象元数据并登记后台校验任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {
    private static final long CONFIRM_GRACE_MINUTES = 5L;

    private final FileAssetMapper fileAssetMapper;
    private final StagedUploadStorage stagedUploadStorage;
    private final FileUploadProperties fileUploadProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> fixedWindowRateLimitScript;
    private final FileJobMapper fileJobMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 签发文件直传预签名凭证并创建待上传文件资产记录（不记录原始文件名）
     *
     * @param uploaderAccountId 已鉴权的上传者账号 ID
     * @param accountType       上传者账号端类型（用于匹配限流额度）
     * @param kind              文件类型
     * @param contentType       声明的文件 MIME 类型
     * @param byteSize          声明的文件精确字节数
     * @return 包含资产主键 ID 与预签名 PUT 凭证的结果对象
     */
    public IssuedFileUpload issueUpload(
            long uploaderAccountId,
            AccountType accountType,
            FileKind kind,
            String contentType,
            long byteSize) {
        return issueUpload(uploaderAccountId, accountType, kind, contentType, byteSize, null);
    }

    /**
     * 签发文件直传预签名凭证并创建待上传文件资产记录
     *
     * @param uploaderAccountId 已鉴权的上传者账号 ID
     * @param accountType       上传者账号端类型（用于匹配限流额度）
     * @param kind              文件类型
     * @param contentType       声明的文件 MIME 类型
     * @param byteSize          声明的文件精确字节数
     * @param originalFilename  可选原始文件名（自动剥离路径前缀并拦截非法控制字符）
     * @return 包含资产主键 ID 与预签名 PUT 凭证的结果对象
     */
    public IssuedFileUpload issueUpload(
            long uploaderAccountId,
            AccountType accountType,
            FileKind kind,
            String contentType,
            long byteSize,
            String originalFilename) {
        if (uploaderAccountId <= 0L) {
            throw new IllegalArgumentException("uploaderAccountId 必须大于 0");
        }
        if (accountType == null) {
            throw new IllegalArgumentException("accountType 不能为空");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind 不能为空");
        }
        if (!StringUtils.hasText(contentType)) {
            throw new BusinessException(ApiCode.VALIDATION_FAILED, "文件 Content-Type 不能为空");
        }

        // 校验当前文件类型允许的 MIME 范围、大小上限并规范化原始文件名
        String normalizedContentType = contentType.strip().toLowerCase(Locale.ROOT);
        long maxBytes = validateUploadConstraint(kind, normalizedContentType, byteSize);
        String normalizedFilename = FileUtils.normalizeOriginalFilename(originalFilename);

        // 扣减当前账号在时间窗口内的签发配额
        consumeSignQuota(uploaderAccountId, accountType);

        // 生成按文件类型与 UTC 日期划分的暂存区对象键并签发预签名 PUT
        String stagingObjectKey = FileObjectPathUtils.stagingKey(kind, LocalDate.now(ZoneOffset.UTC), UUID.randomUUID());
        PresignedUpload upload = stagedUploadStorage.presignPut(
                stagingObjectKey,
                normalizedContentType,
                byteSize,
                maxBytes);

        // 持久化待确认状态的文件资产记录，暂存路径独立保存在 staging_key（正式 object_key 归档后写入）
        OffsetDateTime confirmExpiresAt = upload
                .expiresAt()
                .atOffset(ZoneOffset.UTC)
                .plusMinutes(CONFIRM_GRACE_MINUTES);

        FileAsset fileAsset = new FileAsset();
        fileAsset.setUploaderAccountId(uploaderAccountId);
        fileAsset.setKind(kind.name());
        fileAsset.setStatus(FileAssetStatus.PENDING_UPLOAD.name());
        fileAsset.setStorageScope(FileStorageScope.PRIVATE.name());
        fileAsset.setStagingKey(stagingObjectKey);
        fileAsset.setContentType(normalizedContentType);
        fileAsset.setByteSize(byteSize);
        fileAsset.setOriginalFilename(normalizedFilename);
        fileAsset.setConfirmExpiresAt(confirmExpiresAt);
        fileAssetMapper.insert(fileAsset);

        log.info("签发文件直传凭证成功: assetId={}, kind={}, uploaderAccountId={}, accountType={}, byteSize={}",
                fileAsset.getId(), kind, uploaderAccountId, accountType, byteSize);
        return new IssuedFileUpload(fileAsset.getId(), upload);
    }

    /**
     * 核对暂存区对象元数据并确认上传完成，已确认的请求幂等返回当前状态
     *
     * @param assetId           文件资产主键 ID
     * @param uploaderAccountId 上传者账号 ID
     * @return 当前文件资产状态
     */
    public FileAssetStatus confirmUpload(long assetId, long uploaderAccountId) {
        if (assetId <= 0L) {
            throw new IllegalArgumentException("assetId 必须大于 0");
        }
        if (uploaderAccountId <= 0L) {
            throw new IllegalArgumentException("uploaderAccountId 必须大于 0");
        }

        // 查询归属当前账号的文件资产；已确认幂等返回当前状态，已过期直接返回 FILE_UPLOAD_EXPIRED
        FileAsset fileAsset = findByIdAndUploader(assetId, uploaderAccountId);
        if (fileAsset == null) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_FOUND);
        }
        if (fileAsset.getConfirmedAt() != null) {
            log.debug("同账号重复确认, 幂等返回当前状态: assetId={}, accountId={}, status={}",
                    fileAsset.getId(), uploaderAccountId, fileAsset.getStatus());
            return FileAssetStatus.valueOf(fileAsset.getStatus());
        }

        OffsetDateTime checkTime = OffsetDateTime.now(ZoneOffset.UTC);
        if (FileAssetStatus.EXPIRED.name().equals(fileAsset.getStatus())
                || !fileAsset.getConfirmExpiresAt().isAfter(checkTime)) {
            throw new BusinessException(FileApiCode.FILE_UPLOAD_EXPIRED);
        }
        if (!FileAssetStatus.PENDING_UPLOAD.name().equals(fileAsset.getStatus())) {
            throw new IllegalStateException("文件资产状态异常, 无法执行确认: assetId=" + assetId + ", status=" + fileAsset.getStatus());
        }

        // 在事务外通过 HEAD 核对暂存对象的大小、MIME 与 ETag，避免长事务占用数据库连接
        Optional<StagedObjectMetadata> metadataOptional = stagedUploadStorage.find(fileAsset.getStagingKey());
        if (metadataOptional.isEmpty()) {
            throw new BusinessException(FileApiCode.FILE_UPLOAD_OBJECT_MISSING);
        }
        StagedObjectMetadata metadata = metadataOptional.get();
        if (metadata.byteSize() != fileAsset.getByteSize()
                || !FileUtils.isSameMediaType(fileAsset.getContentType(), metadata.contentType())
                || !StringUtils.hasText(metadata.eTag())) {
            throw new BusinessException(FileApiCode.FILE_UPLOAD_METADATA_MISMATCH);
        }

        // 在短事务内以 PENDING_UPLOAD 与未过期为条件原子推进状态，写入暂存 ETag 并登记 VALIDATE_UPLOAD 任务
        OffsetDateTime confirmedAt = OffsetDateTime.now(ZoneOffset.UTC);
        Boolean confirmed = transactionTemplate.execute(status -> {
            int updatedRows = fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                    .eq(FileAsset::getId, assetId)
                    .eq(FileAsset::getUploaderAccountId, uploaderAccountId)
                    .eq(FileAsset::getStatus, FileAssetStatus.PENDING_UPLOAD.name())
                    .isNull(FileAsset::getConfirmedAt)
                    .gt(FileAsset::getConfirmExpiresAt, confirmedAt)
                    .set(FileAsset::getStatus, FileAssetStatus.UPLOAD_CONFIRMED.name())
                    .set(FileAsset::getConfirmedAt, confirmedAt)
                    .set(FileAsset::getStagingEtag, metadata.eTag())
                    .set(FileAsset::getUpdatedAt, confirmedAt));
            if (updatedRows != 1) {
                return Boolean.FALSE;
            }

            FileJob fileJob = new FileJob();
            fileJob.setAssetId(assetId);
            fileJob.setType(FileJobType.VALIDATE_UPLOAD.name());
            fileJob.setStatus(FileJobStatus.PENDING.name());
            fileJob.setAttemptCount(0);
            fileJob.setAvailableAt(confirmedAt);
            fileJob.setDispatchAfter(confirmedAt);
            fileJob.setDedupKey(assetId + ":" + FileJobType.VALIDATE_UPLOAD.name());
            fileJobMapper.insertIfNotActive(fileJob, confirmedAt);
            return Boolean.TRUE;
        });

        if (Boolean.TRUE.equals(confirmed)) {
            log.info("确认文件上传成功: assetId={}, uploaderAccountId={}, byteSize={}",
                    assetId, uploaderAccountId, metadata.byteSize());
            return FileAssetStatus.UPLOAD_CONFIRMED;
        }

        // 并发更新未命中时回查最新状态，区分并发幂等成功、临界过期或异常状态
        FileAsset latestAsset = findByIdAndUploader(assetId, uploaderAccountId);
        if (latestAsset == null) {
            throw new BusinessException(FileApiCode.FILE_ASSET_NOT_FOUND);
        }
        if (latestAsset.getConfirmedAt() != null) {
            return FileAssetStatus.valueOf(latestAsset.getStatus());
        }
        if (FileAssetStatus.EXPIRED.name().equals(latestAsset.getStatus())
                || !latestAsset.getConfirmExpiresAt().isAfter(confirmedAt)) {
            throw new BusinessException(FileApiCode.FILE_UPLOAD_EXPIRED);
        }
        throw new IllegalStateException("文件资产状态异常, 无法完成确认: assetId=" + assetId + ", status=" + latestAsset.getStatus());
    }

    /**
     * 按文件类型校验允许的 MIME 类型与单文件字节大小上限
     *
     * @param kind                  文件类型
     * @param normalizedContentType 规范化小写 MIME 类型
     * @param byteSize              声明的文件字节大小
     * @return 该文件类型允许的最大字节数上限
     */
    private long validateUploadConstraint(FileKind kind, String normalizedContentType, long byteSize) {
        return switch (kind) {
            case IMAGE -> {
                if (!ImageContentType.isSupported(normalizedContentType)) {
                    throw new BusinessException(ApiCode.VALIDATION_FAILED, "不支持的图片 Content-Type: " + normalizedContentType);
                }
                long maxImageBytes = fileUploadProperties.getMaxImageBytes();
                if (byteSize < 1L || byteSize > maxImageBytes) {
                    throw new BusinessException(ApiCode.VALIDATION_FAILED, "图片字节数必须在 1 到 " + maxImageBytes + " 之间");
                }
                yield maxImageBytes;
            }
        };
    }

    private FileAsset findByIdAndUploader(long assetId, long uploaderAccountId) {
        return fileAssetMapper.selectOne(new LambdaQueryWrapper<FileAsset>()
                .eq(FileAsset::getId, assetId)
                .eq(FileAsset::getUploaderAccountId, uploaderAccountId));
    }

    /**
     * 校验并扣减指定账号在当前时间窗口内的文件上传签名配额
     *
     * @param uploaderAccountId 上传者账号 ID
     * @param accountType       上传者账号端类型
     */
    private void consumeSignQuota(long uploaderAccountId, AccountType accountType) {
        String rateLimitKey = FileRedisConstants.UPLOAD_SIGN_KEY_PREFIX
                + accountType.name().toLowerCase(Locale.ROOT) + ":"
                + uploaderAccountId;

        long signLimit = accountType == AccountType.ADMIN
                ? fileUploadProperties.getAdminSignLimit()
                : fileUploadProperties.getUserSignLimit();

        try {
            Long remainingMillis = stringRedisTemplate.execute(
                    fixedWindowRateLimitScript,
                    List.of(rateLimitKey),
                    String.valueOf(signLimit),
                    String.valueOf(FileRedisConstants.UPLOAD_SIGN_WINDOW_MILLIS));
            if (remainingMillis == null) {
                log.warn("文件上传签发限流脚本返回空值, key={}", rateLimitKey);
                throw new BusinessException(FileApiCode.FILE_UPLOAD_LIMIT_UNAVAILABLE);
            }
            if (remainingMillis > 0L) {
                long retryAfterSeconds = (remainingMillis + 999L) / 1000L;
                throw new BusinessException(FileApiCode.FILE_UPLOAD_RATE_LIMITED,
                        "上传签名请求过于频繁, 请" + retryAfterSeconds + "秒后重试");
            }
        } catch (DataAccessException ex) {
            log.error("执行文件上传签发限流脚本失败, key={}", rateLimitKey, ex);
            throw new BusinessException(FileApiCode.FILE_UPLOAD_LIMIT_UNAVAILABLE);
        }
    }
}
