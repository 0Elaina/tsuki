package com.nep.tsuki.category.model.dto;

import java.util.List;

import com.nep.tsuki.category.constants.CategoryValidationConstants;
import com.nep.tsuki.category.model.enums.CategoryStatus;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CategoryBatchStatusUpdateRequest(
                @NotEmpty(message = "分类 ID 列表不能为空") @Size(max = CategoryValidationConstants.BATCH_MAX_SIZE, message = "单次最多操作"
                                + CategoryValidationConstants.BATCH_MAX_SIZE
                                + "个分类") List<@NotNull(message = "分类 ID 不能为空") @Positive(message = "分类 ID 必须为正整数") Long> ids,
                @NotNull(message = "分类状态不能为空") CategoryStatus status) {

}
