package com.nep.tsuki.category.model.dto;

import java.util.Objects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @NotBlank(message = "分区名称不能为空") @Size(max = 64, message = "分区名称不能超过64个字符") String name,
        @PositiveOrZero(message = "排序值不能为负数") Integer sortOrder) {
    public CategoryCreateRequest {
        if (name != null) {
            name = name.strip();
        }
        sortOrder = Objects.requireNonNullElse(sortOrder, 0);
    }
}
