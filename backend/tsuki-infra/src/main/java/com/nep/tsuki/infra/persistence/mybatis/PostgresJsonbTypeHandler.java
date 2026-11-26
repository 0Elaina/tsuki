package com.nep.tsuki.infra.persistence.mybatis;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;

public class PostgresJsonbTypeHandler extends Jackson3TypeHandler {
    public PostgresJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    public PostgresJsonbTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        jsonObject.setValue(toJson(parameter));
        ps.setObject(i, jsonObject);
    }
}
