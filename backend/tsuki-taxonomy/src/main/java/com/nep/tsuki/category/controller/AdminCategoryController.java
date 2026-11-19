package com.nep.tsuki.category.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nep.tsuki.common.api.ApiResponse;
import com.nep.tsuki.common.api.PageResult;
import com.nep.tsuki.category.model.dto.CategoryBatchDeleteRequest;
import com.nep.tsuki.category.model.dto.CategoryBatchStatusUpdateRequest;
import com.nep.tsuki.category.model.dto.CategoryCreateRequest;
import com.nep.tsuki.category.model.dto.CategoryQuery;
import com.nep.tsuki.category.model.dto.CategoryStatusUpdateRequest;
import com.nep.tsuki.category.model.dto.CategoryUpdateRequest;
import com.nep.tsuki.category.model.vo.CategoryResponse;
import com.nep.tsuki.category.service.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Tag(name = "管理端分类接口")
@RestController
@Validated
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {
    private final CategoryService categoryService;

    /**
     * 新增分类
     *
     * @param request 新增分类请求
     * @return 新增的分类信息
     */
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增分类")
    @PostMapping
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
        return ApiResponse.success(
                categoryService.create(request.name(), request.sortOrder()));
    }

    /**
     * 根据 ID 查询分类
     *
     * @param id 分类 ID
     * @return 查询的分类信息
     */
    @Operation(summary = "查询分类详情")
    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> findById(@PathVariable @Positive(message = "分类 ID 必须为正整数") Long id) {
        return ApiResponse.success(categoryService.findById(id));
    }

    /**
     * 分页查询分类列表
     *
     * @param query 查询参数
     * @return 分页查询结果
     */
    @Operation(summary = "分页查询分类列表")
    @GetMapping
    public ApiResponse<PageResult<CategoryResponse>> list(
            @ParameterObject @Valid @ModelAttribute CategoryQuery query) {
        return ApiResponse.success(categoryService.list(query));
    }

    /**
     * 编辑分类
     *
     * @param id      分类 ID
     * @param request 编辑请求
     * @return 编辑后的分类信息
     */
    @Operation(summary = "编辑分类")
    @PatchMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable @Positive(message = "分类 ID 必须为正整数") Long id,
            @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.success(categoryService.update(id, request.name(), request.sortOrder()));
    }

    /**
     * 更新分类状态
     *
     * @param id      分类 ID
     * @param request 更新请求
     * @return 更新的分类信息
     */
    @Operation(summary = "更新分类状态")
    @PutMapping("/{id}/status")
    public ApiResponse<CategoryResponse> updateStatus(@PathVariable @Positive(message = "分类 ID 必须为正整数") Long id,
            @Valid @RequestBody CategoryStatusUpdateRequest request) {
        return ApiResponse.success(categoryService.updateStatus(id, request.status()));
    }

    /**
     * 删除分类
     *
     * @param id 分类 ID
     * @return 删除结果
     */
    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive(message = "分类 ID 必须为正整数") Long id) {
        categoryService.delete(id);
        return ApiResponse.success();
    }

    /**
     * 批量更新分类状态
     *
     * @param request 批量更新请求
     * @return 更新的数量
     */
    @Operation(summary = "批量更新分类状态")
    @PostMapping("/batch-status")
    public ApiResponse<Integer> batchUpdateStatus(
            @Valid @RequestBody CategoryBatchStatusUpdateRequest request) {
        return ApiResponse.success(categoryService.batchUpdateStatus(request.ids(), request.status()));
    }

    /**
     * 批量删除分类
     *
     * @param request 批量删除请求
     * @return 删除的数量
     */
    @Operation(summary = "批量删除分类")
    @PostMapping("/batch-delete")
    public ApiResponse<Integer> batchDelete(
            @Valid @RequestBody CategoryBatchDeleteRequest request) {
        return ApiResponse.success(categoryService.batchDelete(request.ids()));
    }
}
