package com.nep.tsuki.file.model.enums;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum FileJobType {

    /** 暂存原件校验、探测与归档 */
    VALIDATE_UPLOAD(FileJobQueue.INTERACTIVE, Duration.ofSeconds(30L), false),
    /** 按需生成文件衍生物 */
    GENERATE_VARIANT(FileJobQueue.INTERACTIVE, Duration.ofSeconds(60L), true),
    /** 物理清理整份资产及其未清理衍生物 */
    CLEANUP_ASSET(FileJobQueue.CLEANUP, Duration.ofMinutes(5L), false),
    /** 物理清理单个已撤回衍生物 */
    CLEANUP_VARIANT(FileJobQueue.CLEANUP, Duration.ofMinutes(5L), true);

    /** 所属 RabbitMQ 队列 */
    private final FileJobQueue queue;
    /** Broker 确认后未领取的补派等待时长 */
    private final Duration redeliveryDelay;
    /** 是否必须关联 variant_id */
    private final boolean requiresVariantId;

    public static final List<String> INTERACTIVE_TYPE_NAMES = typeNamesFor(FileJobQueue.INTERACTIVE);
    public static final List<String> CLEANUP_TYPE_NAMES = typeNamesFor(FileJobQueue.CLEANUP);

    private static final Map<String, FileJobType> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    public String getRoutingKey() {
        return queue.getRoutingKey();
    }

    public boolean isCleanup() {
        return queue == FileJobQueue.CLEANUP;
    }

    public static List<String> typeNamesFor(FileJobQueue queue) {
        return Arrays.stream(values())
                .filter(type -> type.queue == queue)
                .map(Enum::name)
                .toList();
    }

    public static Optional<FileJobType> findByName(String type) {
        if (!StringUtils.hasText(type)) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(type));
    }
}
