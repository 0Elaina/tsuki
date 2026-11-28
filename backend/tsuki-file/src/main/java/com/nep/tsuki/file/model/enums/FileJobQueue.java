package com.nep.tsuki.file.model.enums;

import com.nep.tsuki.file.constants.FileRabbitConstants;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件异步任务的逻辑队列分类，按业务优先级与消费并发隔离交互任务和后台清理任务
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum FileJobQueue {

    /** 交互类任务队列（原件校验与衍生物生成，优先级高） */
    INTERACTIVE(FileRabbitConstants.INTERACTIVE_QUEUE, FileRabbitConstants.INTERACTIVE_ROUTING_KEY),
    /** 清理类任务队列（资产与衍生物物理对象删除，独立消费并发与持续退避） */
    CLEANUP(FileRabbitConstants.CLEANUP_QUEUE, FileRabbitConstants.CLEANUP_ROUTING_KEY);

    /** 对应的 RabbitMQ 持久化队列名 */
    private final String queueName;
    /** 对应的 RabbitMQ 交换机路由键 */
    private final String routingKey;
}
