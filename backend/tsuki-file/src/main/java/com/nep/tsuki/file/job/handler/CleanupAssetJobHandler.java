package com.nep.tsuki.file.job.handler;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nep.tsuki.file.constants.FileJobConstants;
import com.nep.tsuki.file.job.FileJobRunner;
import com.nep.tsuki.file.mapper.FileVariantMapper;
import com.nep.tsuki.file.model.entity.FileAsset;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.entity.FileVariant;
import com.nep.tsuki.file.model.enums.FileAssetStatus;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.processing.FileJobCode;
import com.nep.tsuki.file.service.FileJobTxService;
import com.nep.tsuki.file.service.FileJobTxService.ClaimedFileJob;
import com.nep.tsuki.file.service.FileJobTxService.CommitOutcome;
import com.nep.tsuki.file.storage.FileObjectStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 整份资产物理清理处理器：按逻辑存储桶分组幂等删除原件及所有未清理衍生物，失败持续自动退避，全部成功后写 purged_at 与 DELETED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupAssetJobHandler implements FileJobHandler {
    private final FileJobTxService fileJobTxService;
    private final FileVariantMapper fileVariantMapper;
    private final FileObjectStorage fileObjectStorage;

    @Override
    public FileJobType supportedType() {
        return FileJobType.CLEANUP_ASSET;
    }

    @Override
    public void handle(ClaimedFileJob claimed) {
        FileJob job = claimed.job();
        FileAsset asset = claimed.asset();

        // 仅清理处于 DELETING 状态的资产，已删除或状态不符时取消任务
        if (asset == null || !FileAssetStatus.DELETING.name().equals(asset.getStatus())) {
            cancelJob(job, "资产不存在或非 DELETING 状态，无需执行物理清理");
            return;
        }

        // 1. 按逻辑存储范围分组收集待删除对象键（S3 DeleteObjects 为单桶接口）
        Map<FileStorageScope, List<String>> keysByScope = new EnumMap<>(FileStorageScope.class);
        if (StringUtils.hasText(asset.getObjectKey())) {
            keysByScope.computeIfAbsent(FileStorageScope.valueOf(asset.getStorageScope()), key -> new ArrayList<>())
                    .add(asset.getObjectKey());
        }

        List<FileVariant> variants = fileVariantMapper.selectList(new LambdaQueryWrapper<FileVariant>()
                .eq(FileVariant::getAssetId, asset.getId())
                .isNull(FileVariant::getPurgedAt));
        for (FileVariant variant : variants) {
            if (StringUtils.hasText(variant.getObjectKey())) {
                keysByScope.computeIfAbsent(FileStorageScope.valueOf(variant.getStorageScope()), key -> new ArrayList<>())
                        .add(variant.getObjectKey());
            }
        }

        // 2. 按桶分别执行严格批量删除，任何非 NoSuchKey 错误均触发同一任务持续退避重排
        try {
            for (Map.Entry<FileStorageScope, List<String>> entry : keysByScope.entrySet()) {
                fileObjectStorage.deleteObjects(entry.getKey(), entry.getValue());
            }
        } catch (IllegalStateException ex) {
            rescheduleCleanup(job, ex);
            return;
        }

        // 3. 所有对象删除确认完成后，在短事务内统一写 purged_at 并将资产推进为 DELETED
        try {
            CommitOutcome outcome = fileJobTxService.completeCleanupAssetSucceeded(job);
            if (outcome == CommitOutcome.COMMITTED) {
                log.info("资产物理清理完成: jobId={}, assetId={}, variantCount={}",
                        job.getId(), asset.getId(), variants.size());
            } else {
                log.warn("资产物理清理提交未落库: jobId={}, assetId={}, outcome={}",
                        job.getId(), asset.getId(), outcome);
            }
        } catch (DataAccessException | TransactionException ex) {
            log.error("提交资产物理清理结果时数据库异常，退避 1 秒后重投: jobId={}, assetId={}",
                    job.getId(), asset.getId(), ex);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("提交资产物理清理结果时数据库异常: jobId=" + job.getId(), ex);
        }
    }

    private void rescheduleCleanup(FileJob job, IllegalStateException ex) {
        if (FileJobConstants.isCleanupSteadyBackoff(job.getAttemptCount())) {
            log.error("资产物理清理失败已进入 6h 稳态退避，请排查存储权限或桶配置: jobId={}, assetId={}, attemptCount={}",
                    job.getId(), job.getAssetId(), job.getAttemptCount(), ex);
        } else {
            log.warn("资产物理清理失败，将按退避策略自动重试: jobId={}, assetId={}, attemptCount={}",
                    job.getId(), job.getAssetId(), job.getAttemptCount(), ex);
        }

        try {
            fileJobTxService.rescheduleCleanupRetry(job, FileJobCode.STORAGE_ERROR, ex.getMessage());
        } catch (DataAccessException | TransactionException dbEx) {
            log.error("重排资产物理清理任务时数据库异常，退避 1 秒后重投: jobId={}", job.getId(), dbEx);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("重排资产物理清理任务时数据库异常: jobId=" + job.getId(), dbEx);
        }
    }

    private void cancelJob(FileJob job, String reason) {
        try {
            fileJobTxService.cancelRunningJob(job, null, reason);
        } catch (DataAccessException | TransactionException ex) {
            log.error("取消资产清理任务时数据库异常，退避 1 秒后重投: jobId={}", job.getId(), ex);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("取消资产清理任务时数据库异常: jobId=" + job.getId(), ex);
        }
    }
}
