package com.nep.tsuki.file.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nep.tsuki.common.constants.TimeConstants;
import com.nep.tsuki.file.mapper.FileAssetMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileAssetCleanupTask {
    private final FileAssetMapper fileAssetMapper;
    public static final String CLEANUP_CRON = "0 0 * * * *";
    public static final long EXPIRED_DAYS = 7L;
    public static final int BATCH_SIZE = 500;
    public static final int MAX_BATCHES_PER_RUN = 10;

    /**
     * 定时分批清理超过保留期限且未确认上传的过期文件资产记录
     */
    @Scheduled(cron = CLEANUP_CRON, zone = TimeConstants.ZONE_SHANGHAI)
    public void cleanupExpiredUnconfirmedAssets() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(EXPIRED_DAYS);
        int totalDeleted = 0;
        int executedBatches = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = fileAssetMapper.deleteExpiredUnconfirmedBatch(cutoff, BATCH_SIZE);
            executedBatches++;
            totalDeleted += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        log.info("清理过期未确认文件资产完成, batches={}, totalDeleted={}, cutoff={}", executedBatches, totalDeleted, cutoff);
    }
}
