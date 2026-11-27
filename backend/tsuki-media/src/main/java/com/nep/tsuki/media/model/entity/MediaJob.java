package com.nep.tsuki.media.model.entity;

import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("media_jobs")
public class MediaJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;
    private String type;
    private String status;

    /** 已尝试执行次数 */
    private Integer attemptCount;

    /** 任务最早允许被调度执行的时间 */
    private OffsetDateTime availableAt;

    /** 当前执行租约的过期截止时间 */
    private OffsetDateTime leaseUntil;

    /** 计划投递时间 */
    private OffsetDateTime dispatchAfter;

    /** 当前派发批次的唯一令牌 */
    private String dispatchToken;

    /** 实际派发到消息队列的时间 */
    private OffsetDateTime dispatchedAt;

    /** 任务执行失败时的错误码 */
    private String failureCode;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}