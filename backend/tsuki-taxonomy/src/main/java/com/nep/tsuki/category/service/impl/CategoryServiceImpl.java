package com.nep.tsuki.category.service.impl;

import java.util.Locale;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nep.tsuki.api.PageResult;
import com.nep.tsuki.category.constants.CategoryApiCode;
import com.nep.tsuki.category.mapper.CategoryMapper;
import com.nep.tsuki.category.model.dto.CategoryQuery;
import com.nep.tsuki.category.model.entity.Category;
import com.nep.tsuki.category.model.enums.CategoryStatus;
import com.nep.tsuki.category.model.vo.CategoryResponse;
import com.nep.tsuki.category.service.CategoryService;
import com.nep.tsuki.exceptions.BusinessException;
import com.nep.tsuki.utils.SqlUtils;

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

    /**
     * 查询分类列表
     *
     * @param query 查询参数
     * @return 分类列表
     */
    @Override
    public PageResult<CategoryResponse> list(CategoryQuery query) {
        Page<Category> page = query.toPage();

        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        if (query.getStatus() != null) {
            wrapper.eq(Category::getStatus, query.getStatus().name());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.apply("LOWER(name) LIKE {0} ESCAPE '!'",
                    SqlUtils.toContainsLikePattern(query.getKeyword().strip().toLowerCase(Locale.ROOT)));
        }
        categoryMapper.selectPage(page, wrapper.orderByAsc(Category::getSortOrder).orderByAsc(Category::getId));
        return PageResult.from(page.convert(CategoryResponse::of));
    }

    /**
     * 更新分类
     *
     * @param id        分类ID
     * @param name      分类名称
     * @param sortOrder 排序顺序
     * @return 分类信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryResponse update(Long id, String name, Integer sortOrder) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSortOrder(sortOrder);

        int updated;
        try {
            updated = categoryMapper.updateById(category);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(CategoryApiCode.CATEGORY_NAME_CONFLICT);
        }

        if (updated == 0) {
            throw new BusinessException(CategoryApiCode.CATEGORY_NOT_FOUND);
        }

        log.info("更新分类成功: id={}, name={}", category.getId(), category.getName());
        return findById(id);
    }

    /**
     * 更新分类状态
     *
     * @param id     分类ID
     * @param status 分类状态
     * @return 分类信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryResponse updateStatus(Long id, CategoryStatus status) {
        Category category = new Category();
        category.setId(id);
        category.setStatus(status.name());

        int updated = categoryMapper.updateById(category);
        if (updated == 0) {
            throw new BusinessException(CategoryApiCode.CATEGORY_NOT_FOUND);
        }
        log.info("更新分类状态成功: id={}, status={}", category.getId(), category.getStatus());
        return findById(id);
    }

    /**
     * 删除分类
     *
     * @param id 分类ID
     */
    @Override
    public void delete(Long id) {
        int deleted = categoryMapper.deleteById(id);
        if (deleted == 0) {
            log.warn("目标分类不存在, 幂等成功, id={}", id);
            return;
        }
        log.info("删除目标分类成功, id={}", id);
    }
}
