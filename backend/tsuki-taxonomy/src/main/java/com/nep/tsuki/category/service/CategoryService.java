package com.nep.tsuki.category.service;

import com.nep.tsuki.api.PageResult;
import com.nep.tsuki.category.model.dto.CategoryQuery;
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
}
