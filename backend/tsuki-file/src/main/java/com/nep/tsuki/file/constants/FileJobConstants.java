package com.nep.tsuki.file.constants;

import java.time.Duration;
import java.util.Optional;

public final class FileJobConstants {
    private FileJobConstants() {
    }

    /** 单次扫描派发的任务批量上限 */
    public static final int DISPATCH_BATCH_SIZE = 50;
    /** 单次定时调度最多轮转派发的轮数上限（每轮依次扫描交互与清理队列） */
    public static final int MAX_DISPATCH_ROUNDS_PER_RUN = 10;
    /** 抢占待派发批次时的租约时间（秒） */
    public static final long DISPATCH_CLAIM_DELAY_SECONDS = 15L;
    /** 投递后等待 Broker 发布确认（Publisher Confirm）的超时时间（秒） */
    public static final long DISPATCH_CONFIRM_TIMEOUT_SECONDS = 3L;

    /** 单次回收过期/卡死任务的批量上限 */
    public static final int REAP_BATCH_SIZE = 50;
    /** 单轮调度最大批量回收次数 */
    public static final int MAX_REAP_BATCHES_PER_RUN = 10;

    /** 校验与生成任务最大尝试次数 */
    public static final int MAX_ATTEMPTS = 3;

    /** 第 1 次失败后的重试退避延迟（秒） */
    public static final long FIRST_RETRY_DELAY_SECONDS = 30L;
    /** 第 2 次失败后的重试退避延迟（秒） */
    public static final long SECOND_RETRY_DELAY_SECONDS = 120L;

    /** 新资产进入 READY 后的未绑定过期宽限期 */
    public static final Duration UNBOUND_ASSET_TTL = Duration.ofHours(24L);

    /** 已结束任务流水的保留天数 */
    public static final long JOB_RETENTION_DAYS = 30L;

    /** 清理任务进入稳态 6h 退避的尝试次数阈值 */
    public static final int CLEANUP_STEADY_BACKOFF_ATTEMPT = 5;

    /** 根据当前已尝试次数计算交互任务下次重试延迟；超过最大重试上限返回 empty */
    public static Optional<Duration> retryDelayAfterAttempt(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> Optional.of(Duration.ofSeconds(FIRST_RETRY_DELAY_SECONDS));
            case 2 -> Optional.of(Duration.ofSeconds(SECOND_RETRY_DELAY_SECONDS));
            default -> Optional.empty();
        };
    }

    /** 计算清理任务失败后的持续退避等待时长（30s -> 2min -> 15min -> 1h -> 6h...） */
    public static Duration cleanupRetryDelayAfterAttempt(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> Duration.ofSeconds(30L);
            case 2 -> Duration.ofMinutes(2L);
            case 3 -> Duration.ofMinutes(15L);
            case 4 -> Duration.ofHours(1L);
            default -> Duration.ofHours(6L);
        };
    }

    /** 判断清理任务是否已进入 6h 稳态退避（需提升至 ERROR 日志级别） */
    public static boolean isCleanupSteadyBackoff(int attemptCount) {
        return attemptCount >= CLEANUP_STEADY_BACKOFF_ATTEMPT;
    }
}
