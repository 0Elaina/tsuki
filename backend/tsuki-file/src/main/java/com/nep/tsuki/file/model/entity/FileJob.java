package com.nep.tsuki.file.model.entity;

import java.time.OffsetDateTime;
import java.util.Map;

import org.apache.ibatis.type.JdbcType;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nep.tsuki.infra.persistence.mybatis.PostgresJsonbTypeHandler;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName(value = "file_jobs", autoResultMap = true)
public class FileJob {

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

    /** 任务扩展参数（按任务类型承载参数化配置） */
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> payload;

    /** 任务执行失败时的错误码 */
    private String failureCode;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
