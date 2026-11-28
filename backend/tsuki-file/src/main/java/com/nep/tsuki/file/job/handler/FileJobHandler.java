package com.nep.tsuki.file.job.handler;

import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.service.FileJobTxService.ClaimedFileJob;

/**
 * 单类文件异步任务处理器契约
 */
public interface FileJobHandler {

    /**
     * 声明本处理器负责执行的任务类型
     *
     * @return 任务类型枚举
     */
    FileJobType supportedType();

    /**
     * 执行已领取租约的文件任务（对象存储 I/O 与媒体处理均在事务外完成）
     *
     * @param claimed 已抢占租约的任务及其关联资产与衍生物上下文
     */
    void handle(ClaimedFileJob claimed);
}
