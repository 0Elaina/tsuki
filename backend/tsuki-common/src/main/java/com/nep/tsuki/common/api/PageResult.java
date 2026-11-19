package com.nep.tsuki.common.api;

import java.util.List;

public record PageResult<T>(List<T> items, long page, long pageSize, long total) {

    public PageResult {
        items = List.copyOf(items);
    }

}
