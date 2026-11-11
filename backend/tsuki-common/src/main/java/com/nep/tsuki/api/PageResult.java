package com.nep.tsuki.api;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public record PageResult<T>(List<T> items, long page, long size, long total) {

    public PageResult {
        items = List.copyOf(items);
    }

    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal());
    }
}
