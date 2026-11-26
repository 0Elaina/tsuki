package com.nep.tsuki.media.mapper;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.media.model.dto.DispatchedMediaJob;
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
}
