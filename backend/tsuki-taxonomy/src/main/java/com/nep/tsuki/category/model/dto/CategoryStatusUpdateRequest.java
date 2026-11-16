package com.nep.tsuki.category.model.dto;

import com.nep.tsuki.category.model.enums.CategoryStatus;

import jakarta.validation.constraints.NotNull;

public record CategoryStatusUpdateRequest(
        @NotNull(message = "分区状态不能为空") CategoryStatus status) {

}
