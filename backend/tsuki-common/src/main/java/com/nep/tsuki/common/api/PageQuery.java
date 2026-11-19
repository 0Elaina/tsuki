package com.nep.tsuki.common.api;

import com.nep.tsuki.common.constants.PageConstants;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageQuery {

    @Min(value = 1, message = "页码必须大于等于 1")
    private Integer page = PageConstants.DEFAULT_PAGE;
    @Min(value = 1, message = "页大小必须大于等于 1")
    @Max(value = PageConstants.MAX_PAGE_SIZE, message = "页大小不能超过 {value}")
    private Integer pageSize = PageConstants.DEFAULT_PAGE_SIZE;

}
