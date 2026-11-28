package com.nep.tsuki.file.model.enums;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import com.nep.tsuki.file.constants.FileRabbitConstants;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum FileJobType {

    /** 暂存原件校验、探测与归档 */
    VALIDATE_UPLOAD(FileRabbitConstants.INTERACTIVE_ROUTING_KEY, Duration.ofSeconds(30L), false, false),
    /** 按需生成文件衍生物 */
    GENERATE_VARIANT(FileRabbitConstants.INTERACTIVE_ROUTING_KEY, Duration.ofSeconds(60L), false, true),
    /** 物理清理整份资产及其未清理衍生物 */
    CLEANUP_ASSET(FileRabbitConstants.CLEANUP_ROUTING_KEY, Duration.ofMinutes(5L), true, false),
    /** 物理清理单个已撤回衍生物 */
    CLEANUP_VARIANT(FileRabbitConstants.CLEANUP_ROUTING_KEY, Duration.ofMinutes(5L), true, true);

    /** 对应的 RabbitMQ 路由键 */
    private final String routingKey;
    /** Broker 确认后未领取的补派等待时长 */
    private final Duration redeliveryDelay;
    /** 是否属于清理队列任务（采用持续退避策略） */
    private final boolean cleanup;
    /** 是否必须关联 variant_id */
    private final boolean requiresVariantId;

    public static final List<FileJobType> INTERACTIVE_TYPES = Arrays.stream(values())
            .filter(type -> !type.cleanup)
            .toList();
    public static final List<FileJobType> CLEANUP_TYPES = Arrays.stream(values())
            .filter(type -> type.cleanup)
            .toList();

    public static final List<String> INTERACTIVE_TYPE_NAMES = INTERACTIVE_TYPES.stream()
            .map(Enum::name)
            .toList();
    public static final List<String> CLEANUP_TYPE_NAMES = CLEANUP_TYPES.stream()
            .map(Enum::name)
            .toList();

    private static final Map<String, FileJobType> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    public static Optional<FileJobType> findByName(String type) {
        if (!StringUtils.hasText(type)) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(type));
    }
}
