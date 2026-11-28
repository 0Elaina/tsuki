package com.nep.tsuki.file.job;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nep.tsuki.file.constants.FileJobConstants;
import com.nep.tsuki.file.mapper.FileVariantMapper;
import com.nep.tsuki.file.model.entity.FileAsset;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.entity.FileVariant;
import com.nep.tsuki.file.model.enums.FileAssetStatus;
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
 * 物理对象清理处理器：统一处理整份资产（CLEANUP_ASSET）与单个已撤回衍生物（CLEANUP_VARIANT）的幂等删除与持续退避重试
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupJobHandler implements FileJobHandler {
    private final FileJobTxService fileJobTxService;
    private final FileVariantMapper fileVariantMapper;
    private final FileObjectStorage fileObjectStorage;

    @Override
    public Set<FileJobType> supportedTypes() {
        return Set.of(FileJobType.CLEANUP_ASSET, FileJobType.CLEANUP_VARIANT);
    }

    @Override
    public void handle(ClaimedFileJob claimed) {
        FileJob job = claimed.job();
        FileJobType jobType = FileJobType.findByName(job.getType())
                .orElseThrow(() -> new IllegalStateException("未知清理任务类型: " + job.getType()));

        switch (jobType) {
            case CLEANUP_ASSET -> handleCleanupAsset(job, claimed.asset());
            case CLEANUP_VARIANT -> handleCleanupVariant(job, claimed.variant());
            default -> throw new IllegalStateException("不支持的清理任务类型: " + jobType);
        }
    }

    private void handleCleanupAsset(FileJob job, FileAsset asset) {
        if (asset == null || !FileAssetStatus.DELETING.name().equals(asset.getStatus())) {
            cancelJob(job, "资产不存在或非 DELETING 状态，无需执行物理清理");
            return;
        }

        // 按逻辑存储范围分组收集待删除对象键（S3 DeleteObjects 为单桶接口）
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

        try {
            for (Map.Entry<FileStorageScope, List<String>> entry : keysByScope.entrySet()) {
                fileObjectStorage.deleteObjects(entry.getKey(), entry.getValue());
            }
        } catch (IllegalStateException ex) {
            rescheduleCleanup(job, ex);
            return;
        }

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

    private void handleCleanupVariant(FileJob job, FileVariant variant) {
        if (variant == null
                || variant.getPurgedAt() != null
                || !FileVariantStatus.WITHDRAWN.name().equals(variant.getStatus())) {
            cancelJob(job, "衍生物已清理或非 WITHDRAWN 状态，无需执行物理清理");
            return;
        }

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
            log.error("物理清理失败已进入 6h 稳态退避，请排查存储权限或桶配置: jobId={}, assetId={}, variantId={}, type={}, attemptCount={}",
                    job.getId(), job.getAssetId(), job.getVariantId(), job.getType(), job.getAttemptCount(), ex);
        } else {
            log.warn("物理清理失败，将按退避策略自动重试: jobId={}, assetId={}, variantId={}, type={}, attemptCount={}",
                    job.getId(), job.getAssetId(), job.getVariantId(), job.getType(), job.getAttemptCount(), ex);
        }

        try {
            fileJobTxService.rescheduleCleanupRetry(job, FileJobCode.STORAGE_ERROR, ex.getMessage());
        } catch (DataAccessException | TransactionException dbEx) {
            log.error("重排物理清理任务时数据库异常，退避 1 秒后重投: jobId={}", job.getId(), dbEx);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("重排物理清理任务时数据库异常: jobId=" + job.getId(), dbEx);
        }
    }

    private void cancelJob(FileJob job, String reason) {
        try {
            fileJobTxService.cancelRunningJob(job, null, reason);
        } catch (DataAccessException | TransactionException ex) {
            log.error("取消清理任务时数据库异常，退避 1 秒后重投: jobId={}", job.getId(), ex);
            FileJobRunner.waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("取消清理任务时数据库异常: jobId=" + job.getId(), ex);
        }
    }
}
