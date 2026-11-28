package com.nep.tsuki.file.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.nep.tsuki.common.constants.TimeConstants;
import com.nep.tsuki.file.constants.FileJobConstants;
import com.nep.tsuki.file.mapper.FileAssetMapper;
import com.nep.tsuki.file.mapper.FileJobMapper;
import com.nep.tsuki.file.service.FileAssetService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件生命周期定时巡检任务，负责未确认上传过期转态、未绑定原件到期入队清理及 30 天历史任务流水回收
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileLifecycleTask {
    private static final int EXPIRE_BATCH_SIZE = 500;
    private static final int UNBOUND_BATCH_SIZE = 100;
    private static final int PURGE_JOB_BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_RUN = 10;
    private static final String PURGE_JOBS_CRON = "0 30 3 * * *";

    private final FileAssetMapper fileAssetMapper;
    private final FileJobMapper fileJobMapper;
    private final FileAssetService fileAssetService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 巡检超过确认期限的 PENDING_UPLOAD 资产并置为 EXPIRED（保留元数据，暂存区字节由 7 天生命周期规则清理）
     */
    @Scheduled(fixedDelay = 60_000L)
    public void expirePendingUploads() {
        int totalExpired = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            Integer updated = transactionTemplate.execute(
                    status -> fileAssetMapper.expirePendingUploadsBatch(now, EXPIRE_BATCH_SIZE));
            int count = updated != null ? updated : 0;
            totalExpired += count;
            if (count < EXPIRE_BATCH_SIZE) {
                break;
            }
        }
        if (totalExpired > 0) {
            log.info("未确认文件上传过期巡检完成: expiredCount={}", totalExpired);
        }
    }

    /**
     * 巡检 READY 后超过 24 小时仍未绑定业务记录的资产：与绑定流程锁同一资产行重查，到期置为 DELETING 并登记 CLEANUP_ASSET
     */
    @Scheduled(fixedDelay = 60_000L)
    public void reclaimExpiredUnboundAssets() {
        int totalReclaimed = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            List<Long> candidateIds = fileAssetMapper.selectExpiredUnboundIds(now, UNBOUND_BATCH_SIZE);
            if (candidateIds.isEmpty()) {
                break;
            }
            for (Long assetId : candidateIds) {
                if (fileAssetService.reclaimExpiredUnboundAsset(assetId)) {
                    totalReclaimed++;
                }
            }
            if (candidateIds.size() < UNBOUND_BATCH_SIZE) {
                break;
            }
        }
        if (totalReclaimed > 0) {
            log.info("未绑定过期原件巡检完成: reclaimedCount={}", totalReclaimed);
        }
    }

    /**
     * 每日分批清理结束已满 30 天的历史任务流水（文件资产与衍生物保留终态记录，不做物理删行）
     */
    @Scheduled(cron = PURGE_JOBS_CRON, zone = TimeConstants.ZONE_SHANGHAI)
    public void purgeFinishedJobs() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(FileJobConstants.JOB_RETENTION_DAYS);
        int totalDeleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            Integer deleted = transactionTemplate.execute(
                    status -> fileJobMapper.purgeFinishedBatch(cutoff, PURGE_JOB_BATCH_SIZE));
            int count = deleted != null ? deleted : 0;
            totalDeleted += count;
            if (count < PURGE_JOB_BATCH_SIZE) {
                break;
            }
        }
        if (totalDeleted > 0) {
            log.info("清理历史文件任务流水完成: deletedCount={}, cutoff={}", totalDeleted, cutoff);
        }
    }
}
