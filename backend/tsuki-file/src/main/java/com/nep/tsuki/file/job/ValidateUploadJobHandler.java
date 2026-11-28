package com.nep.tsuki.file.job;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import com.nep.tsuki.file.constants.FileJobConstants;
import com.nep.tsuki.file.model.entity.FileAsset;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.enums.FileAssetStatus;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.FileKind;
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.processing.FileJobCode;
import com.nep.tsuki.file.processing.FileProcessResult;
import com.nep.tsuki.file.processing.image.ImageProcessor;
import com.nep.tsuki.file.service.FileJobTxService;
import com.nep.tsuki.file.service.FileJobTxService.ClaimedFileJob;
import com.nep.tsuki.file.service.FileJobTxService.CommitOutcome;
import com.nep.tsuki.file.storage.FileObjectStorage;
import com.nep.tsuki.file.utils.FileObjectPathUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 暂存原件校验与归档任务处理器：按暂存 ETag 条件下载校验，通过后用 CopyObject 归档至 originals/
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateUploadJobHandler implements FileJobHandler {
    private final FileJobTxService fileJobTxService;
    private final FileObjectStorage fileObjectStorage;
    private final ImageProcessor imageProcessor;

    @Override
    public Set<FileJobType> supportedTypes() {
        return Set.of(FileJobType.VALIDATE_UPLOAD);
    }

    @Override
    public void handle(ClaimedFileJob claimed) {
        FileJob job = claimed.job();
        FileAsset asset = claimed.asset();
        if (asset == null) {
            log.error("校验任务关联的文件资产不存在: jobId={}, assetId={}", job.getId(), job.getAssetId());
            handleFailure(job, FileJobCode.SOURCE_MISSING, "关联的文件资产不存在: assetId=" + job.getAssetId(), null);
            return;
        }
        if (!FileAssetStatus.UPLOAD_CONFIRMED.name().equals(asset.getStatus())) {
            log.warn("文件资产状态无需继续校验，取消任务: jobId={}, assetId={}, status={}",
                    job.getId(), asset.getId(), asset.getStatus());
            cancelJob(job, "文件资产当前状态不允许执行校验: status=" + asset.getStatus());
            return;
        }

        FileKind kind;
        try {
            kind = FileKind.valueOf(asset.getKind());
        } catch (IllegalArgumentException ex) {
            handleFailure(job, FileJobCode.INVALID_IMAGE, "不支持的文件类型: " + asset.getKind(), null);
            return;
        }

        Path tempFile = null;
        String archivedKey = null;
        try {
            tempFile = Files.createTempFile("tsuki-file-validate-", ".tmp");

            FileJobCode downloadCode;
            try {
                downloadCode = fileObjectStorage.downloadToFile(
                        FileStorageScope.PRIVATE,
                        asset.getStagingKey(),
                        asset.getStagingEtag(),
                        asset.getByteSize(),
                        tempFile);
            } catch (IllegalStateException ex) {
                log.warn("下载暂存原件失败: jobId={}, assetId={}, stagingKey={}",
                        job.getId(), asset.getId(), asset.getStagingKey(), ex);
                handleFailure(job, FileJobCode.STORAGE_ERROR, "下载暂存原件失败: " + ex.getMessage(), null);
                return;
            }

            if (!downloadCode.isSuccess()) {
                log.warn("暂存原件条件读取校验未通过: jobId={}, assetId={}, code={}",
                        job.getId(), asset.getId(), downloadCode.code());
                handleFailure(job, downloadCode, "暂存原件下载校验未通过: " + downloadCode.code(), null);
                return;
            }

            FileProcessResult<Map<String, Object>> probeResult = validateAndProbe(kind, tempFile, asset.getContentType());
            if (!probeResult.isSuccess()) {
                log.warn("原件内容校验未通过: jobId={}, assetId={}, code={}",
                        job.getId(), asset.getId(), probeResult.code().code());
                handleFailure(job, probeResult.code(), "原件内容校验未通过: " + probeResult.code().code(), null);
                return;
            }

            archivedKey = FileObjectPathUtils.originalKey(asset.getId(), job.getId(), job.getAttemptCount());
            FileProcessResult<String> copyResult;
            try {
                copyResult = fileObjectStorage.copyObjectIfMatch(
                        FileStorageScope.PRIVATE,
                        asset.getStagingKey(),
                        asset.getStagingEtag(),
                        FileStorageScope.PRIVATE,
                        archivedKey);
            } catch (IllegalStateException ex) {
                log.warn("CopyObject 归档原件失败: jobId={}, assetId={}, attemptCount={}",
                        job.getId(), asset.getId(), job.getAttemptCount(), ex);
                handleFailure(job, FileJobCode.STORAGE_ERROR, "归档原件失败: " + ex.getMessage(), archivedKey);
                return;
            }

            if (!copyResult.isSuccess()) {
                log.warn("CopyObject 源对象条件不满足，结束校验: jobId={}, assetId={}, code={}",
                        job.getId(), asset.getId(), copyResult.code().code());
                handleFailure(job, copyResult.code(), "归档时源对象已变化或缺失: " + copyResult.code().code(), archivedKey);
                return;
            }

            commitSuccess(job, asset, archivedKey, copyResult.data(), probeResult.data());
        } catch (IOException ex) {
            log.warn("创建原件校验本地临时文件失败: jobId={}, assetId={}", job.getId(), asset.getId(), ex);
            handleFailure(job, FileJobCode.STORAGE_ERROR, "创建本地临时文件失败: " + ex.getMessage(), archivedKey);
        } finally {
            deleteTempFileQuietly(tempFile);
        }
    }

    private FileProcessResult<Map<String, Object>> validateAndProbe(FileKind kind, Path tempFile, String contentType) {
        return switch (kind) {
            case IMAGE -> imageProcessor.validateAndProbe(tempFile, contentType);
        };
    }

    private void commitSuccess(
            FileJob job,
            FileAsset asset,
            String originalKey,
            String archivedEtag,
            Map<String, Object> assetMetadata) {
        try {
            CommitOutcome outcome = fileJobTxService.completeValidateUploadSucceeded(
                    job,
                    originalKey,
                    archivedEtag,
                    assetMetadata);
            switch (outcome) {
                case COMMITTED -> log.info("原件校验归档完成: jobId={}, assetId={}, attemptCount={}, originalKey={}",
                        job.getId(), asset.getId(), job.getAttemptCount(), originalKey);
                case CANCELLED -> {
                    log.warn("原件校验提交时资产已变化，任务已取消并清理本次归档产物: jobId={}, assetId={}, attemptCount={}",
                            job.getId(), asset.getId(), job.getAttemptCount());
                    fileObjectStorage.deleteQuietly(FileStorageScope.PRIVATE, List.of(originalKey));
                }
                case LEASE_LOST -> {
                    log.warn("原件校验提交时租期已失效，放弃落库并清理本次归档产物: jobId={}, assetId={}, attemptCount={}",
                            job.getId(), asset.getId(), job.getAttemptCount());
                    fileObjectStorage.deleteQuietly(FileStorageScope.PRIVATE, List.of(originalKey));
                }
            }
        } catch (DataAccessException | TransactionException ex) {
            // 提交结果不明时先保留产物，待租约回收确认结果后再处理
            log.error("提交原件校验结果时发生数据库异常，保留归档产物并退避 1 秒后重投: jobId={}, assetId={}, originalKey={}",
                    job.getId(), asset.getId(), originalKey, ex);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("提交原件校验结果时数据库异常: jobId=" + job.getId(), ex);
        }
    }

    private void handleFailure(
            FileJob job,
            FileJobCode failureCode,
            String failureMessage,
            String archivedKey) {
        boolean requeue = failureCode.retryable()
                && FileJobConstants.retryDelayAfterAttempt(job.getAttemptCount()).isPresent();

        try {
            CommitOutcome outcome = fileJobTxService.recordValidateUploadFailure(
                    job,
                    failureCode,
                    failureMessage,
                    requeue);
            if (outcome != CommitOutcome.LEASE_LOST) {
                log.warn("原件校验任务未成功: jobId={}, assetId={}, attemptCount={}, failureCode={}, requeue={}, outcome={}",
                        job.getId(), job.getAssetId(), job.getAttemptCount(), failureCode.code(), requeue, outcome);
            }
            if (archivedKey != null) {
                fileObjectStorage.deleteQuietly(FileStorageScope.PRIVATE, List.of(archivedKey));
            }
        } catch (DataAccessException | TransactionException ex) {
            if (archivedKey != null) {
                fileObjectStorage.deleteQuietly(FileStorageScope.PRIVATE, List.of(archivedKey));
            }
            log.error("记录原件校验失败状态时数据库异常，退避 1 秒后重投: jobId={}, failureCode={}",
                    job.getId(), failureCode.code(), ex);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("记录原件校验失败状态时数据库异常: jobId=" + job.getId(), ex);
        }
    }

    private void cancelJob(FileJob job, String reason) {
        try {
            fileJobTxService.cancelRunningJob(job, null, reason);
        } catch (DataAccessException | TransactionException ex) {
            log.error("取消原件校验任务时数据库异常，退避 1 秒后重投: jobId={}", job.getId(), ex);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("取消原件校验任务时数据库异常: jobId=" + job.getId(), ex);
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
