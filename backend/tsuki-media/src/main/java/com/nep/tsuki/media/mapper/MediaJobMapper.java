package com.nep.tsuki.media.mapper;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.media.model.dto.DispatchedMediaJob;
import com.nep.tsuki.media.model.dto.ExpiredMediaJob;
import com.nep.tsuki.media.model.entity.MediaJob;

@Mapper
public interface MediaJobMapper extends BaseMapper<MediaJob> {

    /**
     * 原子抢占一批待派发的媒体处理任务
     *
     * @param supportedTypes 当前节点支持处理的任务类型集合
     * @param now            当前时间
     * @param claimUntil     任务租约截止时间
     * @param batchToken     本次抢占批次的唯一标识令牌
     * @param batchSize      单次最大抢占数量
     * @return 成功抢占并锁定后的任务列表
     */
    List<DispatchedMediaJob> claimDispatchBatch(
            @Param("supportedTypes") List<String> supportedTypes,
            @Param("now") OffsetDateTime now,
            @Param("claimUntil") OffsetDateTime claimUntil,
            @Param("batchToken") String batchToken,
            @Param("batchSize") int batchSize);

    /**
     * 批量确认指定批次的任务已完成派发（清理抢占租约并记录派发时间）
     *
     * @param jobIds       确认已成功派发的任务 ID 列表
     * @param batchToken   抢占批次的唯一标识令牌
     * @param dispatchedAt 派发确认完成时间
     * @return 实际更新的记录数
     */
    int markDispatchedBatch(
            @Param("jobIds") List<Long> jobIds,
            @Param("batchToken") String batchToken,
            @Param("dispatchedAt") OffsetDateTime dispatchedAt);

    /**
     * 查询已过期且当前处于运行中状态的媒体任务，并加行级排他锁
     *
     * @param now       当前基准时间，用于判断任务是否已超过预设的过期时间
     * @param batchSize 单次批量查询并锁定的最大任务数量
     * @return 已过期且处于运行中状态的媒体任务列表
     */
    List<ExpiredMediaJob> selectExpiredRunningForUpdate(
            @Param("now") OffsetDateTime now,
            @Param("batchSize") int batchSize);

    /**
     * 批量将已过期的媒体任务重新调度为待重试状态
     *
     * @param jobIds               待批量重调度的媒体任务主键 ID 列表
     * @param expectedAttemptCount 期望的当前已尝试次数
     * @param nextAvailableAt      下次允许重新调度执行的时间点
     * @param failureCode          本次触发重调度的错误码
     * @param now                  当前基准时间，用于更新记录的修改时间等字段
     * @return 实际成功重调度的媒体任务主键 ID 列表
     */
    List<Long> rescheduleExpiredBatchForRetry(
            @Param("jobIds") List<Long> jobIds,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("nextAvailableAt") OffsetDateTime nextAvailableAt,
            @Param("failureCode") String failureCode,
            @Param("now") OffsetDateTime now);

    /**
     * 批量将已过期的媒体任务标记为最终失败状态
     *
     * @param jobIds      待批量标记为失败的媒体任务主键 ID 列表
     * @param failureCode 导致任务最终失败的错误码
     * @param now         当前基准时间，用于更新记录的修改时间或失败完结时间
     * @return 实际成功标记为失败的媒体任务主键 ID 列表
     */
    List<Long> markExpiredBatchFailed(
            @Param("jobIds") List<Long> jobIds,
            @Param("failureCode") String failureCode,
            @Param("now") OffsetDateTime now);
}
