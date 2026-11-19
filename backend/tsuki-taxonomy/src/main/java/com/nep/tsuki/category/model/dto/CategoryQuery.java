package com.nep.tsuki.category.model.dto;

import com.nep.tsuki.common.api.PageQuery;
import com.nep.tsuki.category.model.enums.CategoryStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryQuery extends PageQuery {
    private String keyword;
    private CategoryStatus status;
}
