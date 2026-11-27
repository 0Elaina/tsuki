package com.nep.tsuki.media.constants;

import java.time.Duration;
import java.util.Optional;

public final class MediaJobConstants {
    private MediaJobConstants() {
    }

    /** 单次扫描派发的任务批量上限 */
    public static final int DISPATCH_BATCH_SIZE = 50;
    /** 抢占任务时的租约/延迟锁定时间（秒） */
    public static final long DISPATCH_CLAIM_DELAY_SECONDS = 15L;
    /** 等待 Broker 发布确认（Publisher Confirm） */
    public static final long DISPATCH_CONFIRM_TIMEOUT_SECONDS = 3L;

    /** 单次回收过期/卡死任务的批量上限 */
    public static final int REAP_BATCH_SIZE = 50;
    /** 最大批量回收次数（防止死循环） */
    public static final int MAX_REAP_BATCHES_PER_RUN = 10;

    /** 任务最大尝试次数 */
    public static final int MAX_ATTEMPTS = 3;

    /** 第 1 次失败后的重试退避延迟（秒） */
    public static final long FIRST_RETRY_DELAY_SECONDS = 30L;

    /** 第 2 次失败后的重试退避延迟（秒） */
    public static final long SECOND_RETRY_DELAY_SECONDS = 120L;

    /** 根据当前已尝试次数计算下次重试延迟；超过最大重试上限返回 empty */
    public static Optional<Duration> retryDelayAfterAttempt(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> Optional.of(Duration.ofSeconds(FIRST_RETRY_DELAY_SECONDS));
            case 2 -> Optional.of(Duration.ofSeconds(SECOND_RETRY_DELAY_SECONDS));
            default -> Optional.empty();
        };
    }
}