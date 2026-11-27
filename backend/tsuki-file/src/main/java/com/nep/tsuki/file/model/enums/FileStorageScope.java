package com.nep.tsuki.file.model.enums;

/**
 * 对象存储逻辑桶范围（按环境映射到对应的私有桶或交付桶）
 */
public enum FileStorageScope {
    /** 私有桶：存放上传暂存对象、归档原件与私有派生规格 */
    PRIVATE,
    /** 交付桶：存放已发布对外交付的对象 */
    DELIVERY
}
