package com.nep.tsuki.category.service;

import com.nep.tsuki.api.PageResult;
import com.nep.tsuki.category.model.dto.CategoryQuery;
import com.nep.tsuki.category.model.enums.CategoryStatus;
import com.nep.tsuki.category.model.vo.CategoryResponse;

public interface CategoryService {
    /**
     * 创建分区
     *
     * @param name      分区名称
     * @param sortOrder 排序顺序
     * @return 分区信息
     */
    CategoryResponse create(String name, Integer sortOrder);

    /**
     * 根据ID查询分区
     *
     * @param id 分区ID
     * @return 分区信息
     */
    CategoryResponse findById(Long id);

    /**
     * 查询分区列表
     *
     * @param query 查询参数
     * @return 分区列表
     */
    PageResult<CategoryResponse> list(CategoryQuery query);

    /**
     * 更新分区
     *
     * @param id        分区ID
     * @param name      分区名称
     * @param sortOrder 排序顺序
     * @return 分区信息
     */
    CategoryResponse update(Long id, String name, Integer sortOrder);

    /**
     * 更新分类状态
     *
     * @param id     分区ID
     * @param status 分区状态
     * @return 分区信息
     */
    CategoryResponse updateStatus(Long id, CategoryStatus status);

    /**
     * 删除分区
     *
     * @param id 分区ID
     */
    void delete(Long id);
}
