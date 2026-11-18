package com.nep.tsuki.category.service;

import java.util.List;

import com.nep.tsuki.api.PageResult;
import com.nep.tsuki.category.model.dto.CategoryQuery;
import com.nep.tsuki.category.model.enums.CategoryStatus;
import com.nep.tsuki.category.model.vo.CategoryResponse;

public interface CategoryService {
    /**
     * 创建分类
     *
     * @param name      分类名称
     * @param sortOrder 排序顺序
     * @return 分类信息
     */
    CategoryResponse create(String name, Integer sortOrder);

    /**
     * 根据ID查询分类
     *
     * @param id 分类ID
     * @return 分类信息
     */
    CategoryResponse findById(Long id);

    /**
     * 查询分类列表
     *
     * @param query 查询参数
     * @return 分类列表
     */
    PageResult<CategoryResponse> list(CategoryQuery query);

    /**
     * 更新分类
     *
     * @param id        分类ID
     * @param name      分类名称
     * @param sortOrder 排序顺序
     * @return 分类信息
     */
    CategoryResponse update(Long id, String name, Integer sortOrder);

    /**
     * 更新分类状态
     *
     * @param id     分类ID
     * @param status 分类状态
     * @return 分类信息
     */
    CategoryResponse updateStatus(Long id, CategoryStatus status);

    /**
     * 删除分类
     *
     * @param id 分类ID
     */
    void delete(Long id);

    /**
     * 批量更新分类状态
     *
     * @param ids    分类ID列表
     * @param status 分类状态
     * @return 影响行数
     */
    int batchUpdateStatus(List<Long> ids, CategoryStatus status);

    /**
     * 批量删除分类
     *
     * @param ids 分类ID列表
     * @return 影响行数
     */
    int batchDelete(List<Long> ids);
}
