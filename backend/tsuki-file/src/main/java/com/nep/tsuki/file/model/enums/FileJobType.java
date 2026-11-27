package com.nep.tsuki.file.model.enums;

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

    /** 图片校验与原件归档任务 */
    PROCESS_IMAGE(FileRabbitConstants.IMAGE_PROCESS_ROUTING_KEY, true);

    /** 对应的 RabbitMQ 路由键 */
    private final String routingKey;
    /** 该任务类型是否负责推进 file_assets 终态 */
    private final boolean advancesAssetStatus;

    public static final List<String> ALL_TYPE_NAMES = Arrays.stream(values())
            .map(FileJobType::name)
            .toList();

    /** 枚举名称到枚举实例的只读缓存映射 */
    private static final Map<String, FileJobType> BY_NAME = Arrays
            .stream(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    /**
     * 根据枚举名称安全查找对应的文件任务类型
     */
    public static Optional<FileJobType> findByName(String type) {
        if (!StringUtils.hasText(type)) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(type));
    }
}
