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
import com.nep.tsuki.file.model.dto.StagingObjectKey;
import com.nep.tsuki.file.model.entity.FileAsset;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.enums.FileAssetStatus;
import com.nep.tsuki.file.model.enums.FileJobStatus;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.FileKind;
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.model.enums.ImageContentType;
import com.nep.tsuki.file.storage.StagedUploadStorage;
import com.nep.tsuki.file.utils.FileUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
     * 签发图片直传预签名凭证并创建待上传文件资产记录（不记录原始文件名）
     *
     * @param uploaderAccountId 上传者账号 ID
     * @param accountType       上传者账号类型
     * @param contentType       图片 MIME 类型
     * @param byteSize          图片字节大小
     * @return 包含文件资产 ID 与直传凭证的结果对象
     */
    public IssuedFileUpload issueImageUpload(
            long uploaderAccountId,
            AccountType accountType,
            String contentType,
            long byteSize) {
        return issueImageUpload(uploaderAccountId, accountType, contentType, byteSize, null);
    }

    /**
     * 签发图片直传预签名凭证并创建待上传文件资产记录
     *
     * @param uploaderAccountId 上传者账号 ID
     * @param accountType       上传者账号类型
     * @param contentType       图片 MIME 类型
     * @param byteSize          图片字节大小
     * @param originalFilename  原始文件名（可选，自动剥离目录前缀并做字符与长度校验）
     * @return 包含文件资产 ID 与直传凭证的结果对象
     */
    public IssuedFileUpload issueImageUpload(
            long uploaderAccountId,
            AccountType accountType,
            String contentType,
            long byteSize,
            String originalFilename) {
        if (uploaderAccountId <= 0L) {
            throw new IllegalArgumentException("uploaderAccountId 必须大于 0");
        }
        if (accountType == null) {
            throw new IllegalArgumentException("accountType 不能为空");
        }

        if (!StringUtils.hasText(contentType)) {
            throw new BusinessException(ApiCode.VALIDATION_FAILED, "图片 Content-Type 不能为空");
        }
        String normalizedContentType = contentType.strip().toLowerCase(Locale.ROOT);
        if (!ImageContentType.isSupported(normalizedContentType)) {
            throw new BusinessException(ApiCode.VALIDATION_FAILED, "不支持的图片 Content-Type: " + normalizedContentType);
        }
        long maxImageBytes = fileUploadProperties.getMaxImageBytes();
        if (byteSize < 1L || byteSize > maxImageBytes) {
            throw new BusinessException(ApiCode.VALIDATION_FAILED, "图片字节数必须在 1 到 " + maxImageBytes + " 之间");
        }
        String normalizedFilename = FileUtils.normalizeOriginalFilename(originalFilename);

        // 扣减当前账号在时间窗口内的签发配额
        consumeSignQuota(uploaderAccountId, accountType);

        // 生成按 UTC 日期分区的对象键及对象存储 PUT 预签名凭证
        StagingObjectKey stagingObjectKey = StagingObjectKey.forImage(LocalDate.now(ZoneOffset.UTC), UUID.randomUUID());
        PresignedUpload upload = stagedUploadStorage.presignPut(
                stagingObjectKey,
                normalizedContentType,
                byteSize,
                maxImageBytes);

        // 持久化待确认状态的文件资产记录
        OffsetDateTime confirmExpiresAt = upload
                .expiresAt()
                .atOffset(ZoneOffset.UTC)
                .plusMinutes(CONFIRM_GRACE_MINUTES);
        FileAsset fileAsset = new FileAsset();
        fileAsset.setUploaderAccountId(uploaderAccountId);
        fileAsset.setKind(FileKind.IMAGE.name());
        fileAsset.setStatus(FileAssetStatus.PENDING_UPLOAD.name());
        fileAsset.setStorageScope(FileStorageScope.PRIVATE.name());
        fileAsset.setObjectKey(stagingObjectKey.value());
        fileAsset.setContentType(normalizedContentType);
        fileAsset.setByteSize(byteSize);
        fileAsset.setOriginalFilename(normalizedFilename);
        fileAsset.setConfirmExpiresAt(confirmExpiresAt);
        fileAssetMapper.insert(fileAsset);

        log.info("签发图片直传凭证成功: assetId={}, uploaderAccountId={}, accountType={}, byteSize={}", fileAsset.getId(),
                uploaderAccountId, accountType, byteSize);
        return new IssuedFileUpload(fileAsset.getId(), upload);
    }

    /**
     * 校验暂存区对象元数据并确认图片上传完成
     *
     * @param assetId           文件资产 ID
     * @param uploaderAccountId 上传者账号 ID
     */
    public void confirmImageUpload(long assetId, long uploaderAccountId) {
        if (assetId <= 0L) {
            throw new IllegalArgumentException("assetId 必须大于 0");
        }
        if (uploaderAccountId <= 0L) {
            throw new IllegalArgumentException("uploaderAccountId 必须大于 0");
        }

        // 查询归属当前账号的文件资产，校验类型匹配，已确认则幂等返回，已过期则拒绝确认
        FileAsset fileAsset = fileAssetMapper.selectOne(new LambdaQueryWrapper<FileAsset>()
                .eq(FileAsset::getId, assetId)
                .eq(FileAsset::getUploaderAccountId, uploaderAccountId));
        if (fileAsset == null) {
            throw new BusinessException(ApiCode.NOT_FOUND);
        }
        if (!FileKind.IMAGE.name().equals(fileAsset.getKind())) {
            throw new BusinessException(ApiCode.VALIDATION_FAILED, "文件资产类型与图片确认接口不匹配");
        }
        if (fileAsset.getConfirmedAt() != null) {
            log.debug("同账号重复确认, 幂等跳过: assetId={}, accountId={}", fileAsset.getId(), uploaderAccountId);
            return;
        }
        OffsetDateTime checkTime = OffsetDateTime.now(ZoneOffset.UTC);
        if (!fileAsset.getConfirmExpiresAt().isAfter(checkTime)) {
            throw new BusinessException(FileApiCode.FILE_UPLOAD_EXPIRED);
        }

        // 查询对象存储实际元数据，校验文件是否存在以及大小、MIME 类型是否与签发时一致
        Optional<StagedObjectMetadata> metadataOptional = stagedUploadStorage.find(fileAsset.getObjectKey());
        if (metadataOptional.isEmpty()) {
            throw new BusinessException(FileApiCode.FILE_UPLOAD_OBJECT_MISSING);
        }
        StagedObjectMetadata metadata = metadataOptional.get();
        if (metadata.byteSize() != fileAsset.getByteSize()
                || !FileUtils.isSameMediaType(fileAsset.getContentType(), metadata.contentType())) {
            throw new BusinessException(FileApiCode.FILE_UPLOAD_METADATA_MISMATCH);
        }

        // 基于乐观条件（未确认且未过期）原子更新资产状态与 ETag，并同事务创建图片入库处理任务
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
                    .set(FileAsset::getEtag, metadata.eTag())
                    .set(FileAsset::getUpdatedAt, confirmedAt));
            if (updatedRows != 1) {
                return Boolean.FALSE;
            }

            FileJob fileJob = new FileJob();
            fileJob.setAssetId(assetId);
            fileJob.setType(FileJobType.PROCESS_IMAGE.name());
            fileJob.setStatus(FileJobStatus.PENDING.name());
            fileJob.setAttemptCount(0);
            fileJob.setAvailableAt(confirmedAt);
            fileJob.setDispatchAfter(confirmedAt);
            fileJob.setDedupKey(assetId + ":" + FileJobType.PROCESS_IMAGE.name());
            fileJobMapper.insertIfNotActive(fileJob, confirmedAt);

            return Boolean.TRUE;
        });

        if (Boolean.TRUE.equals(confirmed)) {
            log.info("确认图片上传成功: assetId={}, uploaderAccountId={}, byteSize={}", assetId, uploaderAccountId,
                    metadata.byteSize());
            return;
        }

        // 并发更新未命中时回查最新状态，区分并发幂等成功、临界过期或异常状态
        FileAsset latestAsset = fileAssetMapper.selectOne(new LambdaQueryWrapper<FileAsset>()
                .eq(FileAsset::getId, assetId)
                .eq(FileAsset::getUploaderAccountId, uploaderAccountId));
        if (latestAsset == null) {
            throw new BusinessException(ApiCode.NOT_FOUND);
        }
        if (latestAsset.getConfirmedAt() != null) {
            return;
        }
        if (!latestAsset.getConfirmExpiresAt().isAfter(confirmedAt)) {
            throw new BusinessException(FileApiCode.FILE_UPLOAD_EXPIRED);
        }
        throw new IllegalStateException("文件资产状态异常, 无法完成确认: assetId=" + assetId + ", status=" + latestAsset.getStatus());
    }

    /**
     * 校验并扣减指定账号在当前时间窗口内的文件上传签名配额
     *
     * @param uploaderAccountId 上传者账号 ID
     * @param accountType       上传者账号类型
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
