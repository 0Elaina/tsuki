package com.nep.tsuki.media.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nep.tsuki.common.constants.TimeConstants;
import com.nep.tsuki.media.mapper.MediaAssetMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaAssetCleanupTask {
    private final MediaAssetMapper mediaAssetMapper;
    public static final String CLEANUP_CRON = "0 0 * * * *";
    public static final long EXPIRED_DAYS = 7L;
    public static final int BATCH_SIZE = 500;
    public static final int MAX_BATCHES_PER_RUN = 10;

    /**
     * 定时分批清理超过保留期限且未确认上传的过期媒体资产记录
     */
    @Scheduled(cron = CLEANUP_CRON, zone = TimeConstants.ZONE_SHANGHAI)
    public void cleanupExpiredUnconfirmedAssets() {
        // 计算过期清理截止时间（当前 UTC 时间往前推 EXPIRED_DAYS 天）
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(EXPIRED_DAYS);
        int totalDeleted = 0;
        int executedBatches = 0;
        // 分批删除以控制单次事务锁范围与数据库负载
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = mediaAssetMapper.deleteExpiredUnconfirmedBatch(cutoff, BATCH_SIZE);
            executedBatches++;
            totalDeleted += deleted;
            // 删除条数不足单批上限说明已无更多过期数据，提前结束循环
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        log.info("清理过期未确认媒体资产完成, batches={}, totalDeleted={}, cutoff={}", executedBatches, totalDeleted, cutoff);
    }
}
