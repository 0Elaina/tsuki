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
 * 文件任务消息消费入口：解析任务 ID 后按队列分派给 {@link FileJobRunner}
 */
@Component
@RequiredArgsConstructor
public class FileJobConsumer {
    private final FileJobRunner fileJobRunner;

    @RabbitListener(
            queues = FileRabbitConstants.INTERACTIVE_QUEUE,
            containerFactory = FileRabbitConstants.INTERACTIVE_LISTENER_CONTAINER_FACTORY)
    public void onInteractiveJobMessage(Message message) {
        long jobId = parseJobId(message);
        fileJobRunner.run(jobId, FileJobQueue.INTERACTIVE);
    }

    @RabbitListener(
            queues = FileRabbitConstants.CLEANUP_QUEUE,
            containerFactory = FileRabbitConstants.CLEANUP_LISTENER_CONTAINER_FACTORY)
    public void onCleanupJobMessage(Message message) {
        long jobId = parseJobId(message);
        fileJobRunner.run(jobId, FileJobQueue.CLEANUP);
    }

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
