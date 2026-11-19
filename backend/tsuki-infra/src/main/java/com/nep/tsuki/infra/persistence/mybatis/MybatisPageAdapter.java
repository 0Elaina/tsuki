package com.nep.tsuki.infra.persistence.mybatis;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nep.tsuki.common.api.PageQuery;
import com.nep.tsuki.common.api.PageResult;
import com.nep.tsuki.common.constants.PageConstants;

public final class MybatisPageAdapter {
    private MybatisPageAdapter() {
    }

    public static <T> Page<T> toPage(PageQuery query) {
        if (query == null) {
            return new Page<>(
                    PageConstants.DEFAULT_PAGE, PageConstants.DEFAULT_PAGE_SIZE);
        }
        int current = (query.getPage() == null || query.getPage() <= 0) ? PageConstants.DEFAULT_PAGE : query.getPage();
        int size = (query.getPageSize() == null || query.getPageSize() <= 0) ? PageConstants.DEFAULT_PAGE_SIZE
                : query.getPageSize();
        return new Page<>(current, size);
    }

    public static <T> PageResult<T> toResult(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal());
    }
}
