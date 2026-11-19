package com.nep.tsuki.tag.controller;

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
import com.nep.tsuki.tag.model.dto.TagBatchDeleteRequest;
import com.nep.tsuki.tag.model.dto.TagBatchStatusUpdateRequest;
import com.nep.tsuki.tag.model.dto.TagCreateRequest;
import com.nep.tsuki.tag.model.dto.TagQuery;
import com.nep.tsuki.tag.model.dto.TagStatusUpdateRequest;
import com.nep.tsuki.tag.model.dto.TagUpdateRequest;
import com.nep.tsuki.tag.model.vo.TagResponse;
import com.nep.tsuki.tag.service.TagService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Tag(name = "管理端标签接口")
@RestController
@Validated
@RequestMapping("/api/v1/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {
    private final TagService tagService;

    /**
     * 新增标签
     *
     * @param request 新增标签请求
     * @return 新增的标签信息
     */
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增标签")
    @PostMapping
    public ApiResponse<TagResponse> create(@Valid @RequestBody TagCreateRequest request) {
        return ApiResponse.success(tagService.create(request.name(), request.sortOrder()));
    }

    /**
     * 根据 ID 查询标签
     *
     * @param id 标签 ID
     * @return 查询的标签信息
     */
    @Operation(summary = "查询标签详情")
    @GetMapping("/{id}")
    public ApiResponse<TagResponse> findById(@PathVariable @Positive(message = "标签 ID 必须为正整数") Long id) {
        return ApiResponse.success(tagService.findById(id));
    }

    /**
     * 分页查询标签列表
     *
     * @param query 查询参数
     * @return 分页查询结果
     */
    @Operation(summary = "分页查询标签列表")
    @GetMapping
    public ApiResponse<PageResult<TagResponse>> list(
            @ParameterObject @Valid @ModelAttribute TagQuery query) {
        return ApiResponse.success(tagService.list(query));
    }

    /**
     * 编辑标签
     *
     * @param id      标签 ID
     * @param request 编辑请求
     * @return 编辑后的标签信息
     */
    @Operation(summary = "编辑标签")
    @PatchMapping("/{id}")
    public ApiResponse<TagResponse> update(@PathVariable @Positive(message = "标签 ID 必须为正整数") Long id,
            @Valid @RequestBody TagUpdateRequest request) {
        return ApiResponse.success(tagService.update(id, request.name(), request.sortOrder()));
    }

    /**
     * 更新标签状态
     *
     * @param id      标签 ID
     * @param request 更新请求
     * @return 更新的标签信息
     */
    @Operation(summary = "更新标签状态")
    @PutMapping("/{id}/status")
    public ApiResponse<TagResponse> updateStatus(@PathVariable @Positive(message = "标签 ID 必须为正整数") Long id,
            @Valid @RequestBody TagStatusUpdateRequest request) {
        return ApiResponse.success(tagService.updateStatus(id, request.status()));
    }

    /**
     * 删除标签
     *
     * @param id 标签 ID
     * @return 删除结果
     */
    @Operation(summary = "删除标签")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive(message = "标签 ID 必须为正整数") Long id) {
        tagService.delete(id);
        return ApiResponse.success();
    }

    /**
     * 批量更新标签状态
     *
     * @param request 批量更新请求
     * @return 更新的数量
     */
    @Operation(summary = "批量更新标签状态")
    @PostMapping("/batch-status")
    public ApiResponse<Integer> batchUpdateStatus(
            @Valid @RequestBody TagBatchStatusUpdateRequest request) {
        return ApiResponse.success(tagService.batchUpdateStatus(request.ids(), request.status()));
    }

    /**
     * 批量删除标签
     *
     * @param request 批量删除请求
     * @return 删除的数量
     */
    @Operation(summary = "批量删除标签")
    @PostMapping("/batch-delete")
    public ApiResponse<Integer> batchDelete(
            @Valid @RequestBody TagBatchDeleteRequest request) {
        return ApiResponse.success(tagService.batchDelete(request.ids()));
    }
}
