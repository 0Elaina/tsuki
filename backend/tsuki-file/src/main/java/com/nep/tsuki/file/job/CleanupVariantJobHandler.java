package com.nep.tsuki.file.job;

import java.util.List;

import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.util.StringUtils;

import com.nep.tsuki.file.constants.FileJobConstants;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.entity.FileVariant;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.model.enums.FileVariantStatus;
import com.nep.tsuki.file.processing.FileJobCode;
import com.nep.tsuki.file.service.FileJobTxService;
import com.nep.tsuki.file.service.FileJobTxService.ClaimedFileJob;
import com.nep.tsuki.file.service.FileJobTxService.CommitOutcome;
import com.nep.tsuki.file.storage.FileObjectStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 单个已撤回衍生物物理清理处理器：幂等删除目标对象，失败持续自动退避，成功后写入 purged_at 并保持 WITHDRAWN
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupVariantJobHandler implements FileJobHandler {
    private final FileJobTxService fileJobTxService;
    private final FileObjectStorage fileObjectStorage;

    @Override
    public FileJobType supportedType() {
        return FileJobType.CLEANUP_VARIANT;
    }

    @Override
    public void handle(ClaimedFileJob claimed) {
        FileJob job = claimed.job();
        FileVariant variant = claimed.variant();

        // 仅清理处于 WITHDRAWN 且尚未记录 purged_at 的衍生物
        if (variant == null
                || variant.getPurgedAt() != null
                || !FileVariantStatus.WITHDRAWN.name().equals(variant.getStatus())) {
            cancelJob(job, "衍生物已清理或非 WITHDRAWN 状态，无需执行物理清理");
            return;
        }

        // 1. 若衍生物已落盘对象键，则在对应逻辑桶执行严格幂等删除
        if (StringUtils.hasText(variant.getObjectKey())) {
            try {
                fileObjectStorage.deleteObjects(
                        FileStorageScope.valueOf(variant.getStorageScope()),
                        List.of(variant.getObjectKey()));
            } catch (IllegalStateException ex) {
                rescheduleCleanup(job, ex);
                return;
            }
        }

        // 2. 删除完成后在短事务内记录 purged_at（保持 WITHDRAWN 状态）
        try {
            CommitOutcome outcome = fileJobTxService.completeCleanupVariantSucceeded(job);
            if (outcome == CommitOutcome.COMMITTED) {
                log.info("衍生物物理清理完成: jobId={}, assetId={}, variantId={}",
                        job.getId(), job.getAssetId(), variant.getId());
            } else {
                log.warn("衍生物物理清理提交未落库: jobId={}, variantId={}, outcome={}",
                        job.getId(), variant.getId(), outcome);
            }
        } catch (DataAccessException | TransactionException ex) {
            log.error("提交衍生物物理清理结果时数据库异常，退避 1 秒后重投: jobId={}, variantId={}",
                    job.getId(), variant.getId(), ex);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("提交衍生物物理清理结果时数据库异常: jobId=" + job.getId(), ex);
        }
    }

    private void rescheduleCleanup(FileJob job, IllegalStateException ex) {
        if (FileJobConstants.isCleanupSteadyBackoff(job.getAttemptCount())) {
            log.error("衍生物物理清理失败已进入 6h 稳态退避，请排查存储权限或桶配置: jobId={}, assetId={}, variantId={}, attemptCount={}",
                    job.getId(), job.getAssetId(), job.getVariantId(), job.getAttemptCount(), ex);
        } else {
            log.warn("衍生物物理清理失败，将按退避策略自动重试: jobId={}, assetId={}, variantId={}, attemptCount={}",
                    job.getId(), job.getAssetId(), job.getVariantId(), job.getAttemptCount(), ex);
        }

        try {
            fileJobTxService.rescheduleCleanupRetry(job, FileJobCode.STORAGE_ERROR, ex.getMessage());
        } catch (DataAccessException | TransactionException dbEx) {
            log.error("重排衍生物物理清理任务时数据库异常，退避 1 秒后重投: jobId={}", job.getId(), dbEx);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("重排衍生物物理清理任务时数据库异常: jobId=" + job.getId(), dbEx);
        }
    }

    private void cancelJob(FileJob job, String reason) {
        try {
            fileJobTxService.cancelRunningJob(job, null, reason);
        } catch (DataAccessException | TransactionException ex) {
            log.error("取消衍生物清理任务时数据库异常，退避 1 秒后重投: jobId={}", job.getId(), ex);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("取消衍生物清理任务时数据库异常: jobId=" + job.getId(), ex);
        }
    }
}
