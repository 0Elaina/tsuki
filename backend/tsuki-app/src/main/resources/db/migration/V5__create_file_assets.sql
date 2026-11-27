CREATE TABLE file_assets (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    uploader_account_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    storage_scope VARCHAR(16) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    byte_size BIGINT NOT NULL,
    etag VARCHAR(256),
    original_filename VARCHAR(255),
    metadata JSONB,
    confirm_expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_file_assets PRIMARY KEY (id),
    CONSTRAINT uk_file_assets_storage_scope_object_key UNIQUE (storage_scope, object_key),
    CONSTRAINT fk_file_assets_user_accounts FOREIGN KEY (uploader_account_id) REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_file_assets_byte_size CHECK (byte_size > 0)
);
CREATE INDEX idx_file_assets_uploader_account_id ON file_assets (uploader_account_id);
CREATE INDEX idx_file_assets_unconfirmed_expiry ON file_assets (confirm_expires_at, id)
WHERE confirmed_at IS NULL;
COMMENT ON TABLE file_assets IS '文件资产表';
COMMENT ON COLUMN file_assets.id IS '主键 ID';
COMMENT ON COLUMN file_assets.uploader_account_id IS '上传账号 ID';
COMMENT ON COLUMN file_assets.kind IS '文件类型';
COMMENT ON COLUMN file_assets.status IS '资产状态';
COMMENT ON COLUMN file_assets.storage_scope IS '存储桶范围';
COMMENT ON COLUMN file_assets.object_key IS '对象键';
COMMENT ON COLUMN file_assets.content_type IS '内容类型';
COMMENT ON COLUMN file_assets.byte_size IS '字节大小';
COMMENT ON COLUMN file_assets.etag IS '对象 ETag';
COMMENT ON COLUMN file_assets.original_filename IS '原始文件名';
COMMENT ON COLUMN file_assets.metadata IS '原件扩展元数据';
COMMENT ON COLUMN file_assets.confirm_expires_at IS '上传确认截止时间';
COMMENT ON COLUMN file_assets.confirmed_at IS '上传确认时间';
COMMENT ON COLUMN file_assets.created_at IS '创建时间';
COMMENT ON COLUMN file_assets.updated_at IS '更新时间';
