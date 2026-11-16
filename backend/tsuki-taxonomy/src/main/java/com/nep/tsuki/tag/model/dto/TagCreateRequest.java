package com.nep.tsuki.tag.model.dto;

import com.nep.tsuki.tag.constants.TagValidationConstants;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record TagCreateRequest(
        @NotBlank(message = "标签名称不能为空") @Size(max = TagValidationConstants.NAME_MAX_LENGTH, message = "标签名称不能超过64个字符") String name,
        @PositiveOrZero(message = "排序值不能为负数") Integer sortOrder) {
    public TagCreateRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
