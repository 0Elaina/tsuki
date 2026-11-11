package com.nep.tsuki.api;

import java.util.Objects;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageQuery(
        @Min(value = 1, message = "页码必须大于等于 1") Integer page,
        @Min(value = 1, message = "页大小必须大于等于 1")
        @Max(value = PageQuery.MAX_PAGE_SIZE, message = "页大小不能超过 {value}") Integer size) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public PageQuery {
        page = Objects.requireNonNullElse(page, DEFAULT_PAGE);
        size = Objects.requireNonNullElse(size, DEFAULT_PAGE_SIZE);
    }

    public <T> Page<T> toPage() {
        return new Page<>(page, size);
    }
}
