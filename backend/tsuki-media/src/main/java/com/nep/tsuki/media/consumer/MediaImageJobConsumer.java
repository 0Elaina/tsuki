package com.nep.tsuki.media.consumer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.util.StringUtils;

import com.nep.tsuki.media.constants.MediaJobConstants;
import com.nep.tsuki.media.constants.MediaRabbitConstants;
import com.nep.tsuki.media.model.entity.MediaAsset;
import com.nep.tsuki.media.model.entity.MediaJob;
import com.nep.tsuki.media.model.enums.ImageContentType;
import com.nep.tsuki.media.model.enums.MediaAssetStatus;
import com.nep.tsuki.media.model.enums.MediaJobType;
import com.nep.tsuki.media.processing.MediaJobCode;
import com.nep.tsuki.media.processing.image.ImageProcessor;
import com.nep.tsuki.media.service.MediaJobTxService;
import com.nep.tsuki.media.service.MediaJobTxService.ClaimedMediaJob;
import com.nep.tsuki.media.storage.MediaObjectStorage;
import com.nep.tsuki.media.utils.MediaObjectPathUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 图片处理任务消费者，负责消费队列消息、校验并归档原始图片以及维护任务状态流转
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaImageJobConsumer {
    private static final long DB_REQUEUE_BACKOFF_MILLIS = 1000L;

    private final MediaJobTxService mediaJobTxService;
    private final MediaObjectStorage mediaObjectStorage;
    private final ImageProcessor imageProcessor;

    /**
     * 消费图片处理任务消息，执行租约领取、原件下载校验、归档上传及结果提交
     *
     * @param message RabbitMQ 原始消息，消息体为任务主键 ID 的 UTF-8 字符串
     */
    @RabbitListener(queues = MediaRabbitConstants.IMAGE_PROCESS_QUEUE, containerFactory = MediaRabbitConstants.IMAGE_LISTENER_CONTAINER_FACTORY)
    public void onImageJobMessage(Message message) {
        long jobId = parseJobId(message);

        ClaimedMediaJob claimed;
        try {
            claimed = mediaJobTxService.claimForExecution(jobId, MediaJobType.PROCESS_IMAGE);
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

        MediaJob job = claimed.job();
        MediaAsset asset = claimed.asset();
        if (asset == null) {
            log.error("图片处理任务关联的媒体资产不存在: jobId={}, assetId={}", job.getId(), job.getAssetId());
            handleFailure(
                    job,
                    null,
                    MediaJobCode.SOURCE_MISSING,
                    List.of());
            return;
        }
        if (!MediaAssetStatus.UPLOAD_CONFIRMED.name().equals(asset.getStatus())) {
            log.warn("媒体资产状态不允许执行图片处理: jobId={}, assetId={}, status={}", job.getId(), asset.getId(), asset.getStatus());
            handleFailure(
                    job,
                    asset,
                    MediaJobCode.SOURCE_CHANGED,
                    List.of());
            return;
        }

        Path tempFile = null;
        List<String> uploadedKeys = new ArrayList<>();

        try {
            tempFile = Files.createTempFile("tsuki-media-image-", ".tmp");

            // 下载上传区原件并核验 ETag 与字节大小
            MediaJobCode downloadCode;
            try {
                downloadCode = mediaObjectStorage.downloadToFile(
                        asset.getObjectKey(),
                        asset.getEtag(),
                        asset.getByteSize(),
                        tempFile);
            } catch (IllegalStateException ex) {
                log.warn("下载图片原件失败: jobId={}, assetId={}, objectKey={}", job.getId(), asset.getId(),
                        asset.getObjectKey(), ex);
                handleFailure(job, asset, MediaJobCode.STORAGE_ERROR, uploadedKeys);
                return;
            }

            if (!downloadCode.isSuccess()) {
                log.warn("图片原件校验未通过: jobId={}, assetId={}, code={}", job.getId(), asset.getId(), downloadCode.code());
                handleFailure(job, asset, downloadCode, uploadedKeys);
                return;
            }

            // 校验图片二进制内容与声明的 MIME 类型是否合法
            MediaJobCode validationCode = imageProcessor.validate(tempFile, asset.getContentType());
            if (!validationCode.isSuccess()) {
                log.warn("图片文件校验未通过: jobId={}, assetId={}, code={}", job.getId(), asset.getId(), validationCode.code());
                handleFailure(job, asset, validationCode, uploadedKeys);
                return;
            }

            // 将校验通过的原件归档至正式存储路径
            ImageContentType originalContentType = ImageContentType.fromMimeType(asset.getContentType());
            if (originalContentType == null) {
                log.warn("资产声明的图片 MIME 类型不受支持: jobId={}, assetId={}, contentType={}", job.getId(), asset.getId(),
                        asset.getContentType());
                handleFailure(job, asset, MediaJobCode.INVALID_IMAGE, uploadedKeys);
                return;
            }

            String originalKey = MediaObjectPathUtils.imageOriginalKey(
                    asset.getId(),
                    job.getId(),
                    job.getAttemptCount(),
                    originalContentType);
            String archivedEtag;
            try {
                archivedEtag = mediaObjectStorage.archiveObject(
                        asset.getObjectKey(),
                        asset.getEtag(),
                        originalKey,
                        tempFile,
                        asset.getContentType(),
                        asset.getByteSize());
                uploadedKeys.add(originalKey);
            } catch (IllegalStateException ex) {
                log.warn("上传归档原件失败: jobId={}, assetId={}, attemptCount={}", job.getId(), asset.getId(),
                        job.getAttemptCount(), ex);
                handleFailure(job, asset, MediaJobCode.STORAGE_ERROR, uploadedKeys);
                return;
            }

            commitSuccess(
                    job,
                    asset,
                    originalKey,
                    archivedEtag,
                    uploadedKeys);
        } catch (IOException ex) {
            log.warn("创建图片处理临时文件失败: jobId={}, assetId={}", job.getId(), asset.getId(), ex);
            handleFailure(job, asset, MediaJobCode.STORAGE_ERROR, uploadedKeys);
        } finally {
            deleteTempFileQuietly(tempFile);
        }
    }

    /**
     * 解析消息体中的任务主键 ID，格式非法或为空时直接拒绝消息且不重新入队
     *
     * @param message RabbitMQ 原始消息
     * @return 大于 0 的任务主键 ID
     */
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

    /**
     * 提交图片处理成功结果，在短事务中落库任务终态与归档后的资产状态
     * 若租约已失效则放弃入库并清理本次上传对象，若数据库异常则先核对引用再决定清理并触发退避重投
     *
     * @param job          当前执行中的媒体任务
     * @param asset        关联的媒体资产
     * @param originalKey  归档后的原始对象存储键
     * @param archivedEtag 归档后的原始对象 ETag
     * @param uploadedKeys 本次实际已上传至对象存储的全部对象键列表
     */
    private void commitSuccess(
            MediaJob job,
            MediaAsset asset,
            String originalKey,
            String archivedEtag,
            List<String> uploadedKeys) {
        try {
            boolean committed = mediaJobTxService.completeSucceeded(
                    job,
                    asset,
                    originalKey,
                    archivedEtag,
                    List.of());
            if (committed) {
                log.info("图片处理任务完成: jobId={}, assetId={}, attemptCount={}, originalKey={}", job.getId(), asset.getId(),
                        job.getAttemptCount(), originalKey);
            } else {
                // 租期已失效说明任务可能已被回收或重跑，直接清理本次上传的孤立对象
                log.warn("图片处理任务提交时租期已失效，放弃入库并清理本次上传对象: jobId={}, assetId={}, attemptCount={}", job.getId(),
                        asset.getId(), job.getAttemptCount());
                mediaObjectStorage.deleteQuietly(uploadedKeys);
            }
        } catch (DataAccessException | TransactionException ex) {
            // 提交阶段发生数据库异常时结果不明确（可能已提交但响应超时），需先核对引用防止误删
            log.error("提交图片处理成功结果时发生数据库异常，先核对引用再决定清理: jobId={}, assetId={}", job.getId(), asset.getId(), ex);
            cleanupIfUnreferenced(
                    asset.getId(),
                    originalKey,
                    uploadedKeys);
            waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("提交图片处理成功结果时数据库访问异常: jobId=" + job.getId(), ex);
        }
    }

    /**
     * 处理图片任务执行失败，根据错误码与尝试次数决定重排重试或标记终态失败
     * 同时清理本次执行过程中已上传至对象存储的未落库文件
     *
     * @param job          当前执行中的媒体任务
     * @param asset        关联的媒体资产
     * @param failureCode  本次失败对应的业务错误码
     * @param uploadedKeys 本次执行已上传至对象存储的对象键列表
     */
    private void handleFailure(
            MediaJob job,
            MediaAsset asset,
            MediaJobCode failureCode,
            List<String> uploadedKeys) {
        // 仅当错误码允许重试且当前尝试次数未超过最大重试上限时，才重新入队等待重试
        boolean requeue = failureCode.retryable()
                && MediaJobConstants.retryDelayAfterAttempt(job.getAttemptCount()).isPresent();

        try {
            boolean transitioned = mediaJobTxService.recordFailure(
                    job,
                    asset,
                    failureCode,
                    requeue);
            if (transitioned) {
                log.warn("图片处理任务执行未成功: jobId={}, assetId={}, attemptCount={}, failureCode={}, requeue={}", job.getId(),
                        job.getAssetId(), job.getAttemptCount(), failureCode.code(), requeue);
            }
            // 任务已确认失败且未落库，直接清理本次上传的孤立对象
            if (!uploadedKeys.isEmpty()) {
                mediaObjectStorage.deleteQuietly(uploadedKeys);
            }
        } catch (DataAccessException | TransactionException ex) {
            // 失败路径不会将本次上传的对象写入数据库，发生数据库异常时仍可安全清理
            if (!uploadedKeys.isEmpty()) {
                mediaObjectStorage.deleteQuietly(uploadedKeys);
            }
            log.error("保存图片处理失败状态时数据库访问异常，退避 1 秒后重投: jobId={}, failureCode={}", job.getId(), failureCode.code(), ex);
            waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("保存图片处理失败状态时数据库访问异常: jobId=" + job.getId(), ex);
        }
    }

    /**
     * 在数据库提交结果不明确时，先核对本次上传的对象是否已被数据库引用
     * 仅在确认未被引用时静默清理对象存储文件，防止误删已落库的有效资产
     *
     * @param assetId      媒体资产主键 ID
     * @param originalKey  归档后的原始对象存储键
     * @param uploadedKeys 本次实际已上传至对象存储的全部对象键列表
     */
    private void cleanupIfUnreferenced(
            long assetId,
            String originalKey,
            List<String> uploadedKeys) {
        if (uploadedKeys.isEmpty()) {
            return;
        }

        try {
            // 核对资产表与规格表中是否已存在对本次上传对象的引用
            boolean referenced = mediaJobTxService.isAnyObjectReferenced(
                    assetId,
                    originalKey,
                    List.of());
            if (!referenced) {
                mediaObjectStorage.deleteQuietly(uploadedKeys);
            } else {
                log.warn("数据库已保留本次上传对象引用，跳过清理: assetId={}, originalKey={}", assetId, originalKey);
            }
        } catch (DataAccessException | TransactionException checkEx) {
            // 核对本身也失败时采取保守策略，保留对象避免误删，留待后续巡检清理
            log.warn("核对数据库对象引用状态失败，暂不删除已上传对象以防误删: assetId={}", assetId, checkEx);
        }
    }

    /**
     * 在触发 AMQP 立即重投前短暂休眠退避，防止数据库异常期间消息高频重投打满消费线程与数据库
     */
    private void waitBeforeImmediateRequeue() {
        try {
            Thread.sleep(DB_REQUEUE_BACKOFF_MILLIS);
        } catch (InterruptedException ex) {
            // 恢复线程中断标志，交由外层容器感知关闭信号
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 静默清理本地临时文件，忽略删除失败的 IO 异常仅记录告警日志
     *
     * @param tempFile 待删除的临时文件路径（可为 null）
     */
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