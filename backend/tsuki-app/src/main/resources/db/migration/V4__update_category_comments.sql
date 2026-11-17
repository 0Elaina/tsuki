COMMENT ON TABLE categories IS '曲库分类表，保存分类名称、排序值、状态与时间戳';
COMMENT ON COLUMN categories.name IS '分类展示名称，保留原始大小写且大小写不敏感唯一';
COMMENT ON COLUMN categories.status IS '分类状态枚举名称：ACTIVE=启用，DISABLED=停用';
COMMENT ON TABLE user_accounts IS '统一账号表，保存管理端与用户端账号凭据、角色与状态';
