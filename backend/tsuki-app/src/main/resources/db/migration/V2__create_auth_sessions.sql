CREATE TABLE auth_sessions (
    id VARCHAR(36) NOT NULL,
    account_id BIGINT NOT NULL,
    current_refresh_jti VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT pk_auth_sessions PRIMARY KEY (id),
    CONSTRAINT fk_auth_sessions_user_accounts FOREIGN KEY (account_id) REFERENCES user_accounts (id) ON DELETE RESTRICT
);
CREATE INDEX idx_auth_sessions_account_id ON auth_sessions (account_id);
COMMENT ON TABLE auth_sessions IS '认证刷新会话表，保存管理员刷新会话标识、轮换 JTI、有效期及撤销状态';
COMMENT ON COLUMN auth_sessions.id IS '会话主键 ID（应用生成的 UUID 字符串，对应 Refresh JWT 的 sid）';
COMMENT ON COLUMN auth_sessions.account_id IS '关联账号 ID，外键引用 user_accounts.id';
COMMENT ON COLUMN auth_sessions.current_refresh_jti IS '当前有效 Refresh JWT 的 UUID 字符串标识（jti），每次刷新轮换';
COMMENT ON COLUMN auth_sessions.created_at IS '会话创建时间（UTC 带时区时间戳）';
COMMENT ON COLUMN auth_sessions.updated_at IS '会话更新时间（UTC 带时区时间戳）';
COMMENT ON COLUMN auth_sessions.expires_at IS '当前刷新截止时间（UTC 带时区时间戳），成功刷新时重算';
COMMENT ON COLUMN auth_sessions.absolute_expires_at IS '会话绝对总截止时间（UTC 带时区时间戳），登录时固定';
COMMENT ON COLUMN auth_sessions.revoked_at IS '会话撤销时间（UTC 带时区时间戳），为空表示尚未撤销';