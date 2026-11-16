package com.nep.tsuki.tag.model.dto;

import com.nep.tsuki.tag.model.enums.TagStatus;

import jakarta.validation.constraints.NotNull;

public record TagStatusUpdateRequest(
        @NotNull(message = "标签状态不能为空") TagStatus status) {

}
