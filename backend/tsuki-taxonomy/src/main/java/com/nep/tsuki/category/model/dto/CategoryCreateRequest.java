package com.nep.tsuki.category.model.dto;

import java.util.Objects;

import com.nep.tsuki.category.constants.CategoryValidationConstants;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @NotBlank(message = "分类名称不能为空") @Size(max = CategoryValidationConstants.NAME_MAX_LENGTH, message = "分类名称不能超过64个字符") String name,
        @PositiveOrZero(message = "排序值不能为负数") Integer sortOrder) {
    public CategoryCreateRequest {
        if (name != null) {
            name = name.strip();
        }
        sortOrder = Objects.requireNonNullElse(sortOrder, 0);
    }
}
