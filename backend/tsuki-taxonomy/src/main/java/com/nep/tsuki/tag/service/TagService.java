package com.nep.tsuki.tag.service;

import java.util.List;

import com.nep.tsuki.api.PageResult;
import com.nep.tsuki.tag.model.dto.TagQuery;
import com.nep.tsuki.tag.model.enums.TagStatus;
import com.nep.tsuki.tag.model.vo.TagResponse;

public interface TagService {
    /**
     * 创建标签
     *
     * @param name      标签名称
     * @param sortOrder 排序顺序
     * @return 标签信息
     */
    TagResponse create(String name, Integer sortOrder);

    /**
     * 根据 ID 查询标签
     *
     * @param id 标签 ID
     * @return 标签信息
     */
    TagResponse findById(Long id);

    /**
     * 分页查询标签列表
     *
     * @param query 查询参数
     * @return 标签列表
     */
    PageResult<TagResponse> list(TagQuery query);

    /**
     * 更新标签
     *
     * @param id        标签 ID
     * @param name      标签名称
     * @param sortOrder 排序顺序
     * @return 标签信息
     */
    TagResponse update(Long id, String name, Integer sortOrder);

    /**
     * 更新标签状态
     *
     * @param id     标签 ID
     * @param status 标签状态
     * @return 标签信息
     */
    TagResponse updateStatus(Long id, TagStatus status);

    /**
     * 删除标签
     *
     * @param id 标签 ID
     */
    void delete(Long id);

    /**
     * 批量更新标签状态
     *
     * @param ids    标签 ID 列表
     * @param status 标签状态
     * @return 影响行数
     */
    int batchUpdateStatus(List<Long> ids, TagStatus status);

    /**
     * 批量删除标签
     *
     * @param ids 标签 ID 列表
     * @return 影响行数
     */
    int batchDelete(List<Long> ids);
}
