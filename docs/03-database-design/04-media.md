# 媒体模块数据库设计

## media_assets 表

媒体源资产表，保存浏览器直传建档、声明约束、源对象存储定位、确认状态及源对象 ETag。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| uploader_account_id | BIGINT | FK, NOT NULL | 签发与上传所属账号 ID，外键引用 `user_accounts.id` |
| kind | VARCHAR(16) | NOT NULL | 媒体类型枚举名称：`IMAGE`=图片 |
| status | VARCHAR(32) | NOT NULL | 资产上传状态枚举名称：`PENDING_UPLOAD`=待确认上传，`UPLOAD_CONFIRMED`=已确认上传 |
| object_key | VARCHAR(1024) | UNIQUE, NOT NULL | 源对象在对象存储中的完整 S3 键（如 `incoming/image/{UTC年}/{UTC月}/{UTC日}/{uploadId}/source`） |
| content_type | VARCHAR(64) | NOT NULL | 源对象规范化小写 MIME 类型（`image/jpeg`、`image/png` 或 `image/webp`） |
| byte_size | BIGINT | NOT NULL, CHECK (`byte_size > 0`) | 源文件字节大小 |
| etag | VARCHAR(256) | 可空 | HEAD 返回的源对象存储 ETag 原值（保留双引号），首次确认成功后写入 |
| confirm_expires_at | TIMESTAMPTZ | NOT NULL | 上传确认截止时间（UTC 带时区时间戳），签发凭证过期时间加 5 分钟 |
| confirmed_at | TIMESTAMPTZ | 可空 | 首次确认成功时间（UTC 带时区时间戳），为空表示尚未确认 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 记录创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 记录最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_media_assets | 主键唯一 | id | 按资产主键查询确认与外层批量删除定位 |
| uk_media_assets_object_key | 唯一 | object_key | 保证对象存储键与源资产记录一对一绑定，防止重复建档 |
| idx_media_assets_uploader_account_id | 普通 | uploader_account_id | 支撑 `fk_media_assets_user_accounts` 外键删除限制检查（`ON DELETE RESTRICT`）及后续按上传者查询资产 |
| idx_media_assets_unconfirmed_expiry | 部分索引 (`WHERE confirmed_at IS NULL`) | confirm_expires_at, id | 支撑 `MediaAssetCleanupTask` 按截止时间排序跳过锁批量清理过期未确认记录 |

**关系**

| 关系 | 目标表 | 外键字段 | 说明 |
| --- | --- | --- | --- |
| N:1 | user_accounts | uploader_account_id | 资产签发上传所属账号，外键约束 `ON DELETE RESTRICT` |

**建表 SQL**

```sql
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
```
