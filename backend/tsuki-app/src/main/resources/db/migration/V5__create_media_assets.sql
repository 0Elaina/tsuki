CREATE TABLE media_assets (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    uploader_account_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    etag VARCHAR(256),
    confirm_expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_media_assets PRIMARY KEY (id),
    CONSTRAINT uk_media_assets_object_key UNIQUE (object_key),
    CONSTRAINT fk_media_assets_user_accounts FOREIGN KEY (uploader_account_id) REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_media_assets_byte_size CHECK (byte_size > 0)
);
CREATE INDEX idx_media_assets_uploader_account_id ON media_assets (uploader_account_id);
CREATE INDEX idx_media_assets_unconfirmed_expiry ON media_assets (confirm_expires_at, id)
WHERE confirmed_at IS NULL;
COMMENT ON TABLE media_assets IS '媒体资产表';
COMMENT ON COLUMN media_assets.id IS '主键 ID';
COMMENT ON COLUMN media_assets.uploader_account_id IS '上传账号 ID';
COMMENT ON COLUMN media_assets.kind IS '媒体类型';
COMMENT ON COLUMN media_assets.status IS '资产状态';
COMMENT ON COLUMN media_assets.object_key IS '对象键';
COMMENT ON COLUMN media_assets.content_type IS '内容类型';
COMMENT ON COLUMN media_assets.byte_size IS '字节大小';
COMMENT ON COLUMN media_assets.etag IS '对象 ETag';
COMMENT ON COLUMN media_assets.confirm_expires_at IS '上传确认截止时间';
COMMENT ON COLUMN media_assets.confirmed_at IS '上传确认时间';
COMMENT ON COLUMN media_assets.created_at IS '创建时间';
COMMENT ON COLUMN media_assets.updated_at IS '更新时间';