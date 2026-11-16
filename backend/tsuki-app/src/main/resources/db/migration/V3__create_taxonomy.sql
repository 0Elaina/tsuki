CREATE TABLE categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    name VARCHAR(64) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT ck_categories_name CHECK (
        name <> ''
        AND name = btrim(name)
    ),
    CONSTRAINT ck_categories_sort_order CHECK (sort_order >= 0)
);
CREATE UNIQUE INDEX uk_categories_name ON categories (lower(name));
CREATE INDEX idx_categories_sort_order_id ON categories (sort_order, id);
COMMENT ON TABLE categories IS '曲库分区表，保存分区名称、排序值、状态与时间戳';
COMMENT ON COLUMN categories.id IS '主键 ID';
COMMENT ON COLUMN categories.name IS '分区展示名称，保留原始大小写且大小写不敏感唯一';
COMMENT ON COLUMN categories.sort_order IS '排序值，非负整数，数值越小越靠前，允许相同值';
COMMENT ON COLUMN categories.status IS '分区状态枚举名称：ACTIVE=启用，DISABLED=停用';
COMMENT ON COLUMN categories.created_at IS '创建时间（UTC 带时区时间戳）';
COMMENT ON COLUMN categories.updated_at IS '最后更新时间（UTC 带时区时间戳）';
CREATE TABLE tags (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    name VARCHAR(64) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tags PRIMARY KEY (id),
    CONSTRAINT ck_tags_name CHECK (
        name <> ''
        AND name = btrim(name)
    ),
    CONSTRAINT ck_tags_sort_order CHECK (sort_order >= 0)
);
CREATE UNIQUE INDEX uk_tags_name ON tags (lower(name));
CREATE INDEX idx_tags_sort_order_id ON tags (sort_order, id);
COMMENT ON TABLE tags IS '曲库标签表，保存标签名称、排序值、状态与时间戳';
COMMENT ON COLUMN tags.id IS '主键 ID';
COMMENT ON COLUMN tags.name IS '标签展示名称，保留原始大小写且大小写不敏感唯一';
COMMENT ON COLUMN tags.sort_order IS '排序值，非负整数，数值越小越靠前，允许相同值';
COMMENT ON COLUMN tags.status IS '标签状态枚举名称：ACTIVE=启用，DISABLED=停用';
COMMENT ON COLUMN tags.created_at IS '创建时间（UTC 带时区时间戳）';
COMMENT ON COLUMN tags.updated_at IS '最后更新时间（UTC 带时区时间戳）';