package com.nep.tsuki.category.service;

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
}
