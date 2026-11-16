package com.nep.tsuki.category.service.impl;

import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nep.tsuki.category.constants.CategoryApiCode;
import com.nep.tsuki.category.mapper.CategoryMapper;
import com.nep.tsuki.category.model.entity.Category;
import com.nep.tsuki.category.model.enums.CategoryStatus;
import com.nep.tsuki.category.model.vo.CategoryResponse;
import com.nep.tsuki.category.service.CategoryService;
import com.nep.tsuki.exceptions.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryMapper categoryMapper;

    /**
     * 创建分类
     *
     * @param name      分类名称
     * @param sortOrder 排序顺序
     * @return 分类信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryResponse create(String name, Integer sortOrder) {
        Category category = new Category();
        category.setName(name);
        category.setStatus(CategoryStatus.ACTIVE.name());
        category.setSortOrder(Objects.requireNonNullElse(sortOrder, 0));

        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(CategoryApiCode.CATEGORY_NAME_CONFLICT);
        }
        log.info("创建分类成功: id={}, name={}", category.getId(), category.getName());
        return CategoryResponse.of(category);
    }

    /**
     * 根据ID查询分类
     *
     * @param id 分类ID
     * @return 分类信息
     */
    @Override
    public CategoryResponse findById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(CategoryApiCode.CATEGORY_NOT_FOUND);
        }
        return CategoryResponse.of(category);
    }
}
