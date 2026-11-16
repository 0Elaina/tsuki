package com.nep.tsuki.tag.model.dto;

import java.util.List;

import com.nep.tsuki.tag.constants.TagValidationConstants;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TagBatchDeleteRequest(
        @NotEmpty(message = "标签 ID 列表不能为空") @Size(max = TagValidationConstants.BATCH_MAX_SIZE, message = "单次最多操作"
                + TagValidationConstants.BATCH_MAX_SIZE
                + "个标签") List<@NotNull(message = "标签 ID 不能为空") @Positive(message = "标签 ID 必须为正整数") Long> ids) {

}
