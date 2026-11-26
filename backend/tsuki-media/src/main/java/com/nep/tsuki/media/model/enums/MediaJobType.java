package com.nep.tsuki.media.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import com.nep.tsuki.media.constants.MediaRabbitConstants;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum MediaJobType {

    /** 图片处理任务 */
    PROCESS_IMAGE(MediaRabbitConstants.IMAGE_PROCESS_ROUTING_KEY);

    /** 对应的 RabbitMQ 路由键 */
    private final String routingKey;

    public static final List<String> ALL_TYPE_NAMES = Arrays.stream(values())
            .map(MediaJobType::name)
            .toList();

    /** 枚举名称到枚举实例的只读缓存映射 */
    private static final Map<String, MediaJobType> BY_NAME = Arrays
            .stream(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    /**
     * 根据枚举名称安全查找对应的媒体任务类型
     */
    public static Optional<MediaJobType> findByName(String type) {
        if (!StringUtils.hasText(type)) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(type));
    }
}