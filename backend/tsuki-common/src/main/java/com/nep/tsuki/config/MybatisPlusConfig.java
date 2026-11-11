package com.nep.tsuki.config;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.nep.tsuki.api.PageQuery;

@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfig {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        paginationInnerInterceptor.setMaxLimit((long) PageQuery.MAX_PAGE_SIZE);
        mybatisPlusInterceptor.addInnerInterceptor(paginationInnerInterceptor);
        return mybatisPlusInterceptor;
    }

    @Bean
    MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                this.strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, now);
                this.strictUpdateFill(metaObject, "updatedAt", OffsetDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                this.strictUpdateFill(metaObject, "updatedAt", OffsetDateTime.class, now);
            }
        };
    }
}
