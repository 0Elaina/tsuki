package com.nep.tsuki.file.consumer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.util.StringUtils;

import com.nep.tsuki.file.constants.FileJobConstants;
import com.nep.tsuki.file.constants.FileRabbitConstants;
import com.nep.tsuki.file.model.entity.FileAsset;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.enums.FileAssetStatus;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.ImageContentType;
import com.nep.tsuki.file.processing.FileJobCode;
import com.nep.tsuki.file.processing.FileProcessResult;
import com.nep.tsuki.file.processing.image.ImageProcessor;
import com.nep.tsuki.file.service.FileJobTxService;
import com.nep.tsuki.file.service.FileJobTxService.ClaimedFileJob;
import com.nep.tsuki.file.storage.FileObjectStorage;
import com.nep.tsuki.file.utils.FileObjectPathUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 图片处理任务消费者，负责消费队列消息、校验并探测原始图片元数据、归档原件以及维护任务状态流转
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileImageJobConsumer {
    private static final long DB_REQUEUE_BACKOFF_MILLIS = 1000L;

    private final FileJobTxService fileJobTxService;
    private final FileObjectStorage fileObjectStorage;
    private final ImageProcessor imageProcessor;

    /**
     * 消费图片处理任务消息，执行租约领取、原件下载校验、归档上传及结果提交
     *
     * @param message RabbitMQ 原始消息，消息体为任务主键 ID 的 UTF-8 字符串
     */
    @RabbitListener(queues = FileRabbitConstants.IMAGE_PROCESS_QUEUE, containerFactory = FileRabbitConstants.IMAGE_LISTENER_CONTAINER_FACTORY)
    public void onImageJobMessage(Message message) {
        long jobId = parseJobId(message);

        ClaimedFileJob claimed;
        try {
            claimed = fileJobTxService.claimForExecution(jobId, FileJobType.PROCESS_IMAGE);
        } catch (DataAccessException | TransactionException ex) {
            log.error("领取图片处理任务时数据库访问异常，退避 1 秒后重投: jobId={}", jobId, ex);
            waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("领取图片处理任务时数据库访问异常: jobId=" + jobId, ex);
        }

        // 未拿到执行资格通常表示消息重复投递或任务已被其他节点接管，直接跳过
        if (claimed == null) {
            log.debug("未领到图片处理任务执行资格, 跳过重复消息: jobId={}", jobId);
            return;
        }

        FileJob job = claimed.job();
        FileAsset asset = claimed.asset();
        if (asset == null) {
            log.error("图片处理任务关联的文件资产不存在: jobId={}, assetId={}", job.getId(), job.getAssetId());
            handleFailure(
                    job,
                    null,
                    FileJobCode.SOURCE_MISSING,
                    "关联的文件资产不存在: assetId=" + job.getAssetId(),
                    List.of());
            return;
        }
        if (!FileAssetStatus.UPLOAD_CONFIRMED.name().equals(asset.getStatus())) {
            log.warn("文件资产状态不允许执行图片处理: jobId={}, assetId={}, status={}", job.getId(), asset.getId(), asset.getStatus());
            handleFailure(
                    job,
                    asset,
                    FileJobCode.SOURCE_CHANGED,
                    "文件资产当前状态不允许执行处理: status=" + asset.getStatus(),
                    List.of());
            return;
        }

        Path tempFile = null;
        List<String> uploadedKeys = new ArrayList<>();

        try {
            tempFile = Files.createTempFile("tsuki-file-image-", ".tmp");

            // 下载上传区原件并核验 ETag 与字节大小
            FileJobCode downloadCode;
            try {
                downloadCode = fileObjectStorage.downloadToFile(
                        asset.getObjectKey(),
                        asset.getEtag(),
                        asset.getByteSize(),
                        tempFile);
            } catch (IllegalStateException ex) {
                log.warn("下载图片原件失败: jobId={}, assetId={}, objectKey={}", job.getId(), asset.getId(),
                        asset.getObjectKey(), ex);
                handleFailure(job, asset, FileJobCode.STORAGE_ERROR, "下载暂存原件失败: " + ex.getMessage(), uploadedKeys);
                return;
            }

            if (!downloadCode.isSuccess()) {
                log.warn("图片原件校验未通过: jobId={}, assetId={}, code={}", job.getId(), asset.getId(), downloadCode.code());
                handleFailure(job, asset, downloadCode, "暂存原件下载校验未通过: " + downloadCode.code(), uploadedKeys);
                return;
            }

            // 校验图片二进制内容与声明的 MIME 类型，并探测原图宽高元数据
            FileProcessResult<Map<String, Object>> probeResult = imageProcessor.validateAndProbe(
                    tempFile,
                    asset.getContentType());
            if (!probeResult.isSuccess()) {
                log.warn("图片文件校验未通过: jobId={}, assetId={}, code={}", job.getId(), asset.getId(),
                        probeResult.code().code());
                handleFailure(job, asset, probeResult.code(), "图片二进制校验未通过: " + probeResult.code().code(), uploadedKeys);
                return;
            }

            // 将校验通过的原件归档至正式存储路径
            ImageContentType originalContentType = ImageContentType.fromMimeType(asset.getContentType());
            if (originalContentType == null) {
                log.warn("资产声明的图片 MIME 类型不受支持: jobId={}, assetId={}, contentType={}", job.getId(), asset.getId(),
                        asset.getContentType());
                handleFailure(job, asset, FileJobCode.INVALID_IMAGE,
                        "不支持的图片 MIME 类型: " + asset.getContentType(), uploadedKeys);
                return;
            }

            String originalKey = FileObjectPathUtils.imageOriginalKey(
                    asset.getId(),
                    job.getId(),
                    job.getAttemptCount(),
                    originalContentType);
            // 在 PUT 前即登记本次尝试独占的目标键：即便写入已落盘但响应超时丢失，失败分支也能幂等清理
            uploadedKeys.add(originalKey);
            String archivedEtag;
            try {
                archivedEtag = fileObjectStorage.archiveObject(
                        asset.getObjectKey(),
                        asset.getEtag(),
                        originalKey,
                        tempFile,
                        asset.getContentType(),
                        asset.getByteSize());
            } catch (IllegalStateException ex) {
                log.warn("上传归档原件失败: jobId={}, assetId={}, attemptCount={}", job.getId(), asset.getId(),
                        job.getAttemptCount(), ex);
                handleFailure(job, asset, FileJobCode.STORAGE_ERROR, "上传归档原件失败: " + ex.getMessage(), uploadedKeys);
                return;
            }

            commitSuccess(
                    job,
                    asset,
                    originalKey,
                    archivedEtag,
                    probeResult.data(),
                    uploadedKeys);
        } catch (IOException ex) {
            log.warn("创建图片处理临时文件失败: jobId={}, assetId={}", job.getId(), asset.getId(), ex);
            handleFailure(job, asset, FileJobCode.STORAGE_ERROR, "创建本地临时文件失败: " + ex.getMessage(), uploadedKeys);
        } finally {
            deleteTempFileQuietly(tempFile);
        }
    }

    private long parseJobId(Message message) {
        if (message == null || message.getBody() == null) {
            throw new AmqpRejectAndDontRequeueException("图片处理消息体为空");
        }
        String rawBody = new String(message.getBody(), StandardCharsets.UTF_8).strip();
        if (!StringUtils.hasText(rawBody)) {
            throw new AmqpRejectAndDontRequeueException("图片处理任务 ID 为空");
        }

        try {
            long jobId = Long.parseLong(rawBody);
            if (jobId <= 0L) {
                throw new AmqpRejectAndDontRequeueException("图片处理任务 ID 必须大于 0: " + rawBody);
            }
            return jobId;
        } catch (NumberFormatException ex) {
            throw new AmqpRejectAndDontRequeueException("非法的图片处理任务 ID: " + rawBody, ex);
        }
    }

    private void commitSuccess(
            FileJob job,
            FileAsset asset,
            String originalKey,
            String archivedEtag,
            Map<String, Object> assetMetadata,
            List<String> uploadedKeys) {
        try {
            boolean committed = fileJobTxService.completeSucceeded(
                    job,
                    asset,
                    originalKey,
                    archivedEtag,
                    assetMetadata,
                    List.of());
            if (committed) {
                log.info("图片处理任务完成: jobId={}, assetId={}, attemptCount={}, originalKey={}", job.getId(), asset.getId(),
                        job.getAttemptCount(), originalKey);
            } else {
                log.warn("图片处理任务提交时租期已失效，放弃入库并清理本次上传对象: jobId={}, assetId={}, attemptCount={}", job.getId(),
                        asset.getId(), job.getAttemptCount());
                fileObjectStorage.deleteQuietly(uploadedKeys);
            }
        } catch (DataAccessException | TransactionException ex) {
            // 提交结果不明确时不得凭普通 SELECT 主动删对象（可能出现原事务滞后提交成功而对象已被删的竞态）；
            // 此处保留原件并退避重投，若原事务实际回滚，由 FileJobReaper 在租期到期后接管并清理本次尝试对象
            log.error("提交图片处理成功结果时发生数据库异常，保留归档原件并退避 1 秒后重投: jobId={}, assetId={}, originalKey={}",
                    job.getId(), asset.getId(), originalKey, ex);
            waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("提交图片处理成功结果时数据库访问异常: jobId=" + job.getId(), ex);
        }
    }

    private void handleFailure(
            FileJob job,
            FileAsset asset,
            FileJobCode failureCode,
            String failureMessage,
            List<String> uploadedKeys) {
        boolean requeue = failureCode.retryable()
                && FileJobConstants.retryDelayAfterAttempt(job.getAttemptCount()).isPresent();

        try {
            boolean transitioned = fileJobTxService.recordFailure(
                    job,
                    asset,
                    failureCode,
                    failureMessage,
                    requeue);
            if (transitioned) {
                log.warn("图片处理任务执行未成功: jobId={}, assetId={}, attemptCount={}, failureCode={}, requeue={}", job.getId(),
                        job.getAssetId(), job.getAttemptCount(), failureCode.code(), requeue);
            }
            if (!uploadedKeys.isEmpty()) {
                fileObjectStorage.deleteQuietly(uploadedKeys);
            }
        } catch (DataAccessException | TransactionException ex) {
            if (!uploadedKeys.isEmpty()) {
                fileObjectStorage.deleteQuietly(uploadedKeys);
            }
            log.error("保存图片处理失败状态时数据库访问异常，退避 1 秒后重投: jobId={}, failureCode={}", job.getId(), failureCode.code(), ex);
            waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("保存图片处理失败状态时数据库访问异常: jobId=" + job.getId(), ex);
        }
    }

    private void waitBeforeImmediateRequeue() {
        try {
            Thread.sleep(DB_REQUEUE_BACKOFF_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
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
