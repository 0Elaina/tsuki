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
     * 按主键加行级排他锁查询任务
     */
    FileJob selectByIdForUpdate(@Param("id") long id);

    /**
     * 幂等插入任务：若存在相同活跃防重键（dedup_key 非空且处于 PENDING/RUNNING），则跳过插入并返回 0
     */
    int insertIfNotActive(
            @Param("job") FileJob job,
            @Param("now") OffsetDateTime now);

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
     * 批量确认指定批次的任务已由 Broker 接收并写入下一次补派时间
     */
    int markDispatchedBatch(
            @Param("jobIds") List<Long> jobIds,
            @Param("batchToken") String batchToken,
            @Param("dispatchedAt") OffsetDateTime dispatchedAt,
            @Param("redeliverAfter") OffsetDateTime redeliverAfter);

    /**
     * 查询租期已过期的运行中任务主键列表（不加锁，供 Reaper 按 Asset -> Variant -> Job 锁序逐条回收）
     */
    List<Long> selectExpiredRunningIds(
            @Param("now") OffsetDateTime now,
            @Param("batchSize") int batchSize);

    /**
     * 原子抢占指定任务进入执行状态，清空派发字段并设置执行租约
     */
    FileJob claimForExecution(
            @Param("jobId") long jobId,
            @Param("allowedTypes") List<String> allowedTypes,
            @Param("now") OffsetDateTime now,
            @Param("leaseUntil") OffsetDateTime leaseUntil);

    /**
     * 将运行中的任务标记为执行成功（要求租期尚未过期）
     */
    int markJobSucceeded(
            @Param("jobId") long jobId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("now") OffsetDateTime now);

    /**
     * 将运行中失败的任务重新调度为待重试状态（要求租期尚未过期）
     */
    int rescheduleRunningForRetry(
            @Param("jobId") long jobId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("nextAvailableAt") OffsetDateTime nextAvailableAt,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("now") OffsetDateTime now);

    /**
     * 将运行中的任务标记为最终失败状态（要求租期尚未过期）
     */
    int markRunningFailed(
            @Param("jobId") long jobId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("now") OffsetDateTime now);

    /**
     * 将运行中的任务标记为已取消状态（要求租期尚未过期）
     */
    int markRunningCancelled(
            @Param("jobId") long jobId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("now") OffsetDateTime now);

    /**
     * Reaper 将租期已过期的任务重新调度为待重试状态
     */
    int rescheduleExpiredForRetry(
            @Param("jobId") long jobId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("nextAvailableAt") OffsetDateTime nextAvailableAt,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("now") OffsetDateTime now);

    /**
     * Reaper 将租期已过期的任务标记为终态失败
     */
    int markExpiredFailed(
            @Param("jobId") long jobId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("now") OffsetDateTime now);

    /**
     * Reaper 将租期已过期且目标已失效的任务标记为已取消
     */
    int markExpiredCancelled(
            @Param("jobId") long jobId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("now") OffsetDateTime now);

    /**
     * 直接取消指定资产下尚未领取的待执行任务
     */
    int cancelPendingByAsset(
            @Param("assetId") long assetId,
            @Param("jobTypes") List<String> jobTypes,
            @Param("now") OffsetDateTime now);

    /**
     * 直接取消指定衍生物下尚未领取的待执行任务
     */
    int cancelPendingByVariant(
            @Param("variantId") long variantId,
            @Param("jobTypes") List<String> jobTypes,
            @Param("now") OffsetDateTime now);

    /**
     * 批量删除结束时间已满保留期限的历史任务流水
     */
    int purgeFinishedBatch(@Param("cutoff") OffsetDateTime cutoff, @Param("batchSize") int batchSize);
}
