package com.nep.tsuki.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.auth.model.entity.AuthSession;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSession> {

    /**
     * 根据 ID 查询会话并获取行锁
     * 
     * @param id 会话 ID
     * @return 会话实体
     */
    @Select("""
            SELECT id, account_id, current_refresh_jti, expires_at, absolute_expires_at, revoked_at, created_at, updated_at
            FROM auth_sessions
            WHERE id = #{id}
            FOR UPDATE
            """)
    AuthSession selectByIdForUpdate(@Param("id") String id);
}
