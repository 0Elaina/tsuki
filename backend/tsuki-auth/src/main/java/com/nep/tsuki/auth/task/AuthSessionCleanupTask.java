package com.nep.tsuki.auth.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nep.tsuki.auth.mapper.AuthSessionMapper;
import com.nep.tsuki.constants.ZoneConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSessionCleanupTask {
    private final AuthSessionMapper authSessionMapper;

    public static final String CLEANUP_CRON = "0 0 3 * * *";
    public static final long EXPIRED_DAYS = 7L;

    /**
     * 定时任务：每天凌晨3点清理超过7天的过期认证会话
     */
    @Scheduled(cron = CLEANUP_CRON, zone = ZoneConstants.SHANGHAI)
    public void cleanupExpiredSession() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(EXPIRED_DAYS);
        int deletedCount = authSessionMapper.deleteExpiredBefore(cutoff);
        log.info("清理过期认证会话完成, deleteCount={}", deletedCount);
    }

}
