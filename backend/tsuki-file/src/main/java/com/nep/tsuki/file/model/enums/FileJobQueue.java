package com.nep.tsuki.file.model.enums;

import com.nep.tsuki.file.constants.FileRabbitConstants;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum FileJobQueue {

    /** 交互类任务队列（原件校验与衍生物生成） */
    INTERACTIVE(FileRabbitConstants.INTERACTIVE_QUEUE, FileRabbitConstants.INTERACTIVE_ROUTING_KEY),
    /** 清理类任务队列（资产与衍生物物理删除） */
    CLEANUP(FileRabbitConstants.CLEANUP_QUEUE, FileRabbitConstants.CLEANUP_ROUTING_KEY);

    private final String queueName;
    private final String routingKey;
}
