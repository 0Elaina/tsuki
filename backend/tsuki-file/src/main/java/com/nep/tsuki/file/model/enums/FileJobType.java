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

/**
 * 文件异步任务类型枚举，定义每类任务归属的队列及 Broker 确认后的补派等待时长
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum FileJobType {

    /** 暂存原件校验、探测与 CopyObject 归档 */
    VALIDATE_UPLOAD(FileJobQueue.INTERACTIVE, Duration.ofSeconds(30L)),
    /** 按需生成文件衍生物 */
    GENERATE_VARIANT(FileJobQueue.INTERACTIVE, Duration.ofSeconds(60L)),
    /** 物理清理整份资产及其未清理衍生物 */
    CLEANUP_ASSET(FileJobQueue.CLEANUP, Duration.ofMinutes(5L)),
    /** 物理清理单个已撤回衍生物 */
    CLEANUP_VARIANT(FileJobQueue.CLEANUP, Duration.ofMinutes(5L));

    /** 归属的逻辑队列 */
    private final FileJobQueue queue;
    /** Broker 确认接收但尚未被消费者领取时的补派等待时长 */
    private final Duration redeliveryDelay;

    /** 交互队列下的任务类型名称列表 */
    public static final List<String> INTERACTIVE_TYPE_NAMES = typeNamesFor(FileJobQueue.INTERACTIVE);
    /** 清理队列下的任务类型名称列表 */
    public static final List<String> CLEANUP_TYPE_NAMES = typeNamesFor(FileJobQueue.CLEANUP);

    /** 枚举名称到枚举实例的只读缓存映射 */
    private static final Map<String, FileJobType> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    /**
     * 获取当前任务类型投递到 RabbitMQ 交换机时使用的路由键
     *
     * @return RabbitMQ 路由键
     */
    public String getRoutingKey() {
        return queue.getRoutingKey();
    }

    /**
     * 获取指定队列下全部任务类型的枚举名称列表
     *
     * @param queue 目标逻辑队列
     * @return 该队列对应的任务类型名称列表
     */
    public static List<String> typeNamesFor(FileJobQueue queue) {
        return Arrays.stream(values())
                .filter(type -> type.queue == queue)
                .map(Enum::name)
                .toList();
    }

    /**
     * 根据枚举名称安全查找对应的文件任务类型
     *
     * @param type 数据库中保存的任务类型名称
     * @return 匹配到的任务类型枚举，未匹配时返回空
     */
    public static Optional<FileJobType> findByName(String type) {
        if (!StringUtils.hasText(type)) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(type));
    }
}
