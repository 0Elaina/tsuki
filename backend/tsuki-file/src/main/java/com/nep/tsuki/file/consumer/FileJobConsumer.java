package com.nep.tsuki.file.consumer;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nep.tsuki.file.constants.FileRabbitConstants;
import com.nep.tsuki.file.job.FileJobRunner;
import com.nep.tsuki.file.model.enums.FileJobQueue;

import lombok.RequiredArgsConstructor;

/**
 * 文件任务消息消费入口，负责解析任务主键 ID 并按队列交由 {@link FileJobRunner} 领取与分派
 */
@Component
@RequiredArgsConstructor
public class FileJobConsumer {
    private final FileJobRunner fileJobRunner;

    /**
     * 消费交互队列消息（原件校验与衍生物生成任务）
     *
     * @param message RabbitMQ 原始消息，消息体为任务主键 ID 的 UTF-8 字符串
     */
    @RabbitListener(
            queues = FileRabbitConstants.INTERACTIVE_QUEUE,
            containerFactory = FileRabbitConstants.INTERACTIVE_LISTENER_CONTAINER_FACTORY)
    public void onInteractiveJobMessage(Message message) {
        long jobId = parseJobId(message);
        fileJobRunner.run(jobId, FileJobQueue.INTERACTIVE);
    }

    /**
     * 消费清理队列消息（资产与衍生物物理对象删除任务）
     *
     * @param message RabbitMQ 原始消息，消息体为任务主键 ID 的 UTF-8 字符串
     */
    @RabbitListener(
            queues = FileRabbitConstants.CLEANUP_QUEUE,
            containerFactory = FileRabbitConstants.CLEANUP_LISTENER_CONTAINER_FACTORY)
    public void onCleanupJobMessage(Message message) {
        long jobId = parseJobId(message);
        fileJobRunner.run(jobId, FileJobQueue.CLEANUP);
    }

    /**
     * 校验并解析消息体中的正整数任务 ID，非法毒丸消息直接拒绝并不重投
     *
     * @param message RabbitMQ 原始消息
     * @return 解析得到的任务主键 ID
     */
    private long parseJobId(Message message) {
        if (message == null || message.getBody() == null) {
            throw new AmqpRejectAndDontRequeueException("文件任务消息体为空");
        }
        String rawBody = new String(message.getBody(), StandardCharsets.UTF_8).strip();
        if (!StringUtils.hasText(rawBody)) {
            throw new AmqpRejectAndDontRequeueException("文件任务 ID 为空");
        }
        try {
            long jobId = Long.parseLong(rawBody);
            if (jobId <= 0L) {
                throw new AmqpRejectAndDontRequeueException("文件任务 ID 必须大于 0: " + rawBody);
            }
            return jobId;
        } catch (NumberFormatException ex) {
            throw new AmqpRejectAndDontRequeueException("非法的文件任务 ID: " + rawBody, ex);
        }
    }
}
