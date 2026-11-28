package com.nep.tsuki.file.job;

import java.util.Set;

import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.service.FileJobTxService.ClaimedFileJob;

/**
 * 文件异步任务处理器契约
 */
public interface FileJobHandler {

    /**
     * 声明本处理器负责的任务类型集合
     */
    Set<FileJobType> supportedTypes();

    /**
     * 执行已领取租约的文件任务（对象存储 I/O 与媒体处理均在事务外完成）
     */
    void handle(ClaimedFileJob claimed);
}
