CREATE TABLE user_accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    username VARCHAR(64) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    password_hash TEXT NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_accounts PRIMARY KEY (id),
    CONSTRAINT uk_user_accounts_username UNIQUE (username)
);
COMMENT ON TABLE user_accounts IS '统一账号表，保存管理端与听众端账号凭据、角色与状态';
COMMENT ON COLUMN user_accounts.id IS '主键 ID';
COMMENT ON COLUMN user_accounts.username IS '规范化小写登录用户名';
COMMENT ON COLUMN user_accounts.nickname IS '展示昵称，允许重复';
COMMENT ON COLUMN user_accounts.password_hash IS 'DelegatingPasswordEncoder 格式的密码哈希（{bcrypt}...）';
COMMENT ON COLUMN user_accounts.role IS '账号角色枚举名称：ADMIN=管理员，USER=听众用户';
COMMENT ON COLUMN user_accounts.status IS '账号状态枚举名称：ACTIVE=正常启用，DISABLED=停用';
COMMENT ON COLUMN user_accounts.created_at IS '创建时间（UTC 带时区时间戳）';
COMMENT ON COLUMN user_accounts.updated_at IS '最后更新时间（UTC 带时区时间戳）';
