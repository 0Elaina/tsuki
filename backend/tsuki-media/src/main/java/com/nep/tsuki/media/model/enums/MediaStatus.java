package com.nep.tsuki.media.model.enums;

/**
 * 媒体文件生命周期状态
 */
public enum MediaStatus {

    /**
     * 上传中：已签发上传凭证并创建记录
     */
    UPLOADING,

    /**
     * 已就绪：文件已上传并校验通过，但尚未绑定到具体业务实体（超时未绑定将被回收）
     */
    AVAILABLE,

    /**
     * 已绑定：文件已关联到具体业务对象，处于受保护状态，不会被孤儿文件清理任务回收
     */
    BOUND,

    /**
     * 待清理：文件已被解绑、删除或超时失效，等待异步任务从对象存储中物理删除
     */
    PURGING
}