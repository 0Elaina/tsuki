package com.nep.tsuki.category.controller;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nep.tsuki.api.ApiResponse;
import com.nep.tsuki.category.model.dto.CategoryCreateRequest;
import com.nep.tsuki.category.model.vo.CategoryResponse;
import com.nep.tsuki.category.service.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Tag(name = "管理端分区接口")
@RestController
@Validated
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {
    private final CategoryService categoryService;

    /**
     * 新增分区
     *
     * @param request 新增分区请求
     * @return 新增的分区信息
     */
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增分区")
    @PostMapping
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
        return ApiResponse.success(
                categoryService.create(request.name(), request.sortOrder()));
    }

    /**
     * 根据 ID 查询分区
     *
     * @param id 分区 ID
     * @return 查询的分区信息
     */
    @Operation(summary = "查询分区详情")
    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> findById(@PathVariable @Positive(message = "分区 ID 必须为正整数") Long id) {
        return ApiResponse.success(categoryService.findById(id));
    }
}
