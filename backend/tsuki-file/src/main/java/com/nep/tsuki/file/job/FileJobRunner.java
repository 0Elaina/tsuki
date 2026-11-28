package com.nep.tsuki.file.job;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.service.FileJobTxService;
import com.nep.tsuki.file.service.FileJobTxService.ClaimedFileJob;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件任务统一执行调度器：负责按队列允许的任务类型领取执行租约并分派给对应的 {@link FileJobHandler}
 */
@Slf4j
@Component
public class FileJobRunner {
    private static final long DB_REQUEUE_BACKOFF_MILLIS = 1000L;

    private final FileJobTxService fileJobTxService;
    private final Map<FileJobType, FileJobHandler> handlersByType;

    public FileJobRunner(FileJobTxService fileJobTxService, List<FileJobHandler> handlers) {
        this.fileJobTxService = Objects.requireNonNull(fileJobTxService, "fileJobTxService 不能为空");
        Map<FileJobType, FileJobHandler> handlerMap = new EnumMap<>(FileJobType.class);
        for (FileJobHandler handler : handlers) {
            Collection<FileJobType> types = Objects.requireNonNull(handler.supportedTypes(), "supportedTypes 不能为空");
            for (FileJobType type : types) {
                Objects.requireNonNull(type, "FileJobType 不能为空");
                if (handlerMap.put(type, handler) != null) {
                    throw new IllegalStateException("重复注册文件任务处理器: " + type);
                }
            }
        }
        this.handlersByType = Map.copyOf(handlerMap);
    }

    /**
     * 领取并执行允许类型集合内的任务
     */
    public void run(long jobId, Collection<FileJobType> queueTypes) {
        Objects.requireNonNull(queueTypes, "queueTypes 不能为空");
        List<FileJobType> allowedTypes = queueTypes.stream()
                .filter(handlersByType::containsKey)
                .toList();
        if (allowedTypes.isEmpty()) {
            log.warn("当前队列未注册任何任务处理器，忽略消息: jobId={}, queueTypes={}", jobId, queueTypes);
            return;
        }

        ClaimedFileJob claimed;
        try {
            claimed = fileJobTxService.claimForExecution(jobId, allowedTypes);
        } catch (DataAccessException | TransactionException ex) {
            log.error("领取文件任务时数据库访问异常，退避 1 秒后重投: jobId={}", jobId, ex);
            waitBeforeImmediateRequeue();
            throw new ImmediateRequeueAmqpException("领取文件任务时数据库访问异常: jobId=" + jobId, ex);
        }

        if (claimed == null) {
            log.debug("未领到文件任务执行资格，跳过重复消息: jobId={}", jobId);
            return;
        }

        FileJobType jobType = FileJobType.findByName(claimed.job().getType())
                .orElseThrow(() -> new IllegalStateException("未知文件任务类型: " + claimed.job().getType()));
        FileJobHandler handler = handlersByType.get(jobType);
        if (handler == null) {
            throw new IllegalStateException("未找到文件任务处理器: " + jobType);
        }

        handler.handle(claimed);
    }

    public static void waitBeforeImmediateRequeue() {
        try {
            Thread.sleep(DB_REQUEUE_BACKOFF_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
