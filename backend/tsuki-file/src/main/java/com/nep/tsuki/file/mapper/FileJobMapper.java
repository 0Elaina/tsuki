package com.nep.tsuki.file.mapper;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.file.model.entity.FileJob;

@Mapper
public interface FileJobMapper extends BaseMapper<FileJob> {

    /**
     * 原子抢占一批待派发的文件处理任务
     */
    List<FileJob> claimDispatchBatch(
            @Param("supportedTypes") List<String> supportedTypes,
            @Param("now") OffsetDateTime now,
            @Param("claimUntil") OffsetDateTime claimUntil,
            @Param("batchToken") String batchToken,
            @Param("batchSize") int batchSize);

    /**
     * 批量确认指定批次的任务已完成派发（清理抢占租约并记录派发时间）
     */
    int markDispatchedBatch(
            @Param("jobIds") List<Long> jobIds,
            @Param("batchToken") String batchToken,
            @Param("dispatchedAt") OffsetDateTime dispatchedAt);

    /**
     * 查询已过期且当前处于运行中状态的文件任务，并加行级排他锁
     */
    List<FileJob> selectExpiredRunningForUpdate(
            @Param("now") OffsetDateTime now,
            @Param("batchSize") int batchSize);

    /**
     * 批量将已过期的文件任务重新调度为待重试状态
     */
    List<Long> rescheduleExpiredBatchForRetry(
            @Param("jobIds") List<Long> jobIds,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("nextAvailableAt") OffsetDateTime nextAvailableAt,
            @Param("failureCode") String failureCode,
            @Param("now") OffsetDateTime now);

    /**
     * 批量将已过期的文件任务标记为最终失败状态
     */
    List<Long> markExpiredBatchFailed(
            @Param("jobIds") List<Long> jobIds,
            @Param("failureCode") String failureCode,
            @Param("now") OffsetDateTime now);

    /**
     * 原子抢占指定任务进入执行状态，并设置执行租约截止时间
     */
    FileJob claimForExecution(
            @Param("jobId") long jobId,
            @Param("expectedType") String expectedType,
            @Param("now") OffsetDateTime now,
            @Param("leaseUntil") OffsetDateTime leaseUntil);

    /**
     * 将运行中的任务标记为执行成功
     */
    int markJobSucceeded(
            @Param("jobId") long jobId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("now") OffsetDateTime now);

    /**
     * 将运行中失败的任务重新调度为待重试状态
     */
    int rescheduleRunningForRetry(
            @Param("jobId") long jobId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("nextAvailableAt") OffsetDateTime nextAvailableAt,
            @Param("failureCode") String failureCode,
            @Param("now") OffsetDateTime now);

    /**
     * 将运行中的任务标记为最终失败状态（不再重试）
     */
    int markRunningFailed(
            @Param("jobId") long jobId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("failureCode") String failureCode,
            @Param("now") OffsetDateTime now);
}
