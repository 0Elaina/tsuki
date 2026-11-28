package com.nep.tsuki.file.job;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.util.StringUtils;

import com.nep.tsuki.file.constants.FileJobConstants;
import com.nep.tsuki.file.model.entity.FileAsset;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.entity.FileVariant;
import com.nep.tsuki.file.model.enums.FileAssetStatus;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.FileKind;
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.model.enums.FileVariantStatus;
import com.nep.tsuki.file.model.enums.ImageVariantPreset;
import com.nep.tsuki.file.processing.FileJobCode;
import com.nep.tsuki.file.processing.FileProcessResult;
import com.nep.tsuki.file.processing.image.ImageProcessingResult;
import com.nep.tsuki.file.processing.image.ImageProcessor;
import com.nep.tsuki.file.service.FileJobTxService;
import com.nep.tsuki.file.service.FileJobTxService.ClaimedFileJob;
import com.nep.tsuki.file.service.FileJobTxService.CommitOutcome;
import com.nep.tsuki.file.storage.FileObjectStorage;
import com.nep.tsuki.file.utils.FileObjectPathUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 衍生物生成任务处理器：下载已归档私有原件，按注册预设加工并以普通 PUT 写入尝试级独占路径
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateVariantJobHandler implements FileJobHandler {
    private final FileJobTxService fileJobTxService;
    private final FileObjectStorage fileObjectStorage;
    private final ImageProcessor imageProcessor;

    @Override
    public FileJobType supportedType() {
        return FileJobType.GENERATE_VARIANT;
    }

    @Override
    public void handle(ClaimedFileJob claimed) {
        FileJob job = claimed.job();
        FileAsset asset = claimed.asset();
        FileVariant variant = claimed.variant();

        // 资产已非 READY 或衍生物已撤回/非 PENDING 时直接取消本次任务
        if (asset == null
                || !FileAssetStatus.READY.name().equals(asset.getStatus())
                || variant == null
                || !FileVariantStatus.PENDING.name().equals(variant.getStatus())) {
            log.warn("资产或衍生物状态已变化，取消生成任务: jobId={}, assetId={}, variantId={}",
                    job.getId(), job.getAssetId(), job.getVariantId());
            cancelJob(job, "资产或衍生物当前状态不允许继续生成");
            return;
        }

        if (!StringUtils.hasText(asset.getObjectKey())) {
            handleFailure(job, variant, FileJobCode.SOURCE_MISSING, "原件对象键为空，无法生成衍生物", null);
            return;
        }

        FileKind kind;
        try {
            kind = FileKind.valueOf(asset.getKind());
        } catch (IllegalArgumentException ex) {
            handleFailure(job, variant, FileJobCode.UNSUPPORTED_PRESET, "不支持的文件类型: " + asset.getKind(), null);
            return;
        }
        if (kind != FileKind.IMAGE) {
            handleFailure(job, variant, FileJobCode.UNSUPPORTED_PRESET, "暂不支持该文件类型的衍生物生成: " + kind, null);
            return;
        }

        Optional<ImageVariantPreset> presetOptional = ImageVariantPreset.find(
                variant.getCode(),
                variant.getPresetRevision());
        if (presetOptional.isEmpty()) {
            handleFailure(job, variant, FileJobCode.UNSUPPORTED_PRESET,
                    "未注册的图片衍生物预设: code=" + variant.getCode() + ", revision=" + variant.getPresetRevision(), null);
            return;
        }

        FileStorageScope targetScope = FileStorageScope.valueOf(variant.getStorageScope());
        Path tempFile = null;
        String uploadedKey = null;
        try {
            tempFile = Files.createTempFile("tsuki-file-variant-", ".tmp");

            // 1. 按原件 ETag 条件下载归档原件到本地临时文件
            FileJobCode downloadCode;
            try {
                downloadCode = fileObjectStorage.downloadToFile(
                        FileStorageScope.valueOf(asset.getStorageScope()),
                        asset.getObjectKey(),
                        asset.getEtag(),
                        asset.getByteSize(),
                        tempFile);
            } catch (IllegalStateException ex) {
                log.warn("下载归档原件失败: jobId={}, assetId={}, variantId={}",
                        job.getId(), asset.getId(), variant.getId(), ex);
                handleFailure(job, variant, FileJobCode.STORAGE_ERROR, "下载归档原件失败: " + ex.getMessage(), null);
                return;
            }

            if (!downloadCode.isSuccess()) {
                log.warn("归档原件下载校验未通过: jobId={}, assetId={}, variantId={}, code={}",
                        job.getId(), asset.getId(), variant.getId(), downloadCode.code());
                handleFailure(job, variant, downloadCode, "归档原件下载校验未通过: " + downloadCode.code(), null);
                return;
            }

            // 2. 按注册预设执行 EXIF 校正、缩放与安全重编码
            FileProcessResult<ImageProcessingResult> processResult = imageProcessor.process(
                    tempFile,
                    asset.getContentType(),
                    presetOptional.get());
            if (!processResult.isSuccess()) {
                log.warn("生成图片衍生物失败: jobId={}, assetId={}, variantId={}, code={}",
                        job.getId(), asset.getId(), variant.getId(), processResult.code().code());
                handleFailure(job, variant, processResult.code(),
                        "图片衍生物处理未通过: " + processResult.code().code(), null);
                return;
            }

            // 3. 在普通 PUT 前先记录尝试级独占 Key，确保异常与取消分支可幂等回收
            ImageProcessingResult output = processResult.data();
            uploadedKey = FileObjectPathUtils.variantKey(
                    asset.getId(),
                    variant.getId(),
                    job.getId(),
                    job.getAttemptCount());

            String outputEtag;
            try {
                outputEtag = fileObjectStorage.putObject(
                        targetScope,
                        uploadedKey,
                        output.content(),
                        output.contentType().getMimeType());
            } catch (IllegalStateException ex) {
                log.warn("上传衍生物产物失败: jobId={}, assetId={}, variantId={}, scope={}, key={}",
                        job.getId(), asset.getId(), variant.getId(), targetScope, uploadedKey, ex);
                handleFailure(job, variant, FileJobCode.STORAGE_ERROR, "上传衍生物产物失败: " + ex.getMessage(), uploadedKey);
                return;
            }

            // 4. 图片宽高写入 JSONB 元数据，并在短事务内推进衍生物为 READY
            Map<String, Object> metadata = Map.of(
                    "width", output.width(),
                    "height", output.height());
            commitSuccess(job, variant, targetScope, uploadedKey, output.contentType().getMimeType(),
                    output.content().length, outputEtag, metadata);
        } catch (IOException ex) {
            log.warn("创建衍生物处理本地临时文件失败: jobId={}, assetId={}, variantId={}",
                    job.getId(), asset.getId(), variant.getId(), ex);
            handleFailure(job, variant, FileJobCode.STORAGE_ERROR, "创建本地临时文件失败: " + ex.getMessage(), uploadedKey);
        } finally {
            deleteTempFileQuietly(tempFile);
        }
    }

    private void commitSuccess(
            FileJob job,
            FileVariant variant,
            FileStorageScope targetScope,
            String objectKey,
            String contentType,
            long byteSize,
            String etag,
            Map<String, Object> metadata) {
        try {
            CommitOutcome outcome = fileJobTxService.completeGenerateVariantSucceeded(
                    job,
                    objectKey,
                    contentType,
                    byteSize,
                    etag,
                    metadata);
            switch (outcome) {
                case COMMITTED -> log.info("衍生物生成完成: jobId={}, assetId={}, variantId={}, scope={}, objectKey={}",
                        job.getId(), job.getAssetId(), variant.getId(), targetScope, objectKey);
                case CANCELLED -> {
                    log.warn("衍生物生成提交时目标已撤回或删除，取消任务并清理产物: jobId={}, variantId={}",
                            job.getId(), variant.getId());
                    fileObjectStorage.deleteQuietly(targetScope, List.of(objectKey));
                }
                case LEASE_LOST -> {
                    log.warn("衍生物生成提交时租期已失效，放弃落库并清理产物: jobId={}, variantId={}",
                            job.getId(), variant.getId());
                    fileObjectStorage.deleteQuietly(targetScope, List.of(objectKey));
                }
            }
        } catch (DataAccessException | TransactionException ex) {
            // 数据库提交结果不明时先保留产物，待租期回收确认结果后再处理
            log.error("提交衍生物生成结果时数据库异常，保留产物并退避 1 秒后重投: jobId={}, variantId={}, objectKey={}",
                    job.getId(), variant.getId(), objectKey, ex);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("提交衍生物生成结果时数据库异常: jobId=" + job.getId(), ex);
        }
    }

    private void handleFailure(
            FileJob job,
            FileVariant variant,
            FileJobCode failureCode,
            String failureMessage,
            String uploadedKey) {
        boolean requeue = failureCode.retryable()
                && FileJobConstants.retryDelayAfterAttempt(job.getAttemptCount()).isPresent();
        FileStorageScope targetScope = FileStorageScope.valueOf(variant.getStorageScope());

        try {
            CommitOutcome outcome = fileJobTxService.recordGenerateVariantFailure(
                    job,
                    failureCode,
                    failureMessage,
                    requeue);
            if (outcome != CommitOutcome.LEASE_LOST) {
                log.warn("衍生物生成任务未成功: jobId={}, assetId={}, variantId={}, attemptCount={}, failureCode={}, requeue={}, outcome={}",
                        job.getId(), job.getAssetId(), variant.getId(), job.getAttemptCount(),
                        failureCode.code(), requeue, outcome);
            }
            if (uploadedKey != null) {
                fileObjectStorage.deleteQuietly(targetScope, List.of(uploadedKey));
            }
        } catch (DataAccessException | TransactionException ex) {
            if (uploadedKey != null) {
                fileObjectStorage.deleteQuietly(targetScope, List.of(uploadedKey));
            }
            log.error("记录衍生物生成失败状态时数据库异常，退避 1 秒后重投: jobId={}, variantId={}, failureCode={}",
                    job.getId(), variant.getId(), failureCode.code(), ex);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("记录衍生物生成失败状态时数据库异常: jobId=" + job.getId(), ex);
        }
    }

    private void cancelJob(FileJob job, String reason) {
        try {
            fileJobTxService.cancelRunningJob(job, null, reason);
        } catch (DataAccessException | TransactionException ex) {
            log.error("取消衍生物生成任务时数据库异常，退避 1 秒后重投: jobId={}", job.getId(), ex);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("取消衍生物生成任务时数据库异常: jobId=" + job.getId(), ex);
        }
    }

    private void deleteTempFileQuietly(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ex) {
            log.warn("清理本地临时文件失败: path={}", tempFile, ex);
        }
    }
}
