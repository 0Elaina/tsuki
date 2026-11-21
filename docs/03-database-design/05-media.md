# 媒体模块数据库设计

## media_assets 表

媒体资源主表，记录媒体类型、生命周期状态、到期清理时间与时间戳。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明（枚举须写全映射） |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| kind | VARCHAR(16) | NOT NULL | 媒体资源类型枚举名称：`IMAGE`=图片 |
| status | VARCHAR(16) | NOT NULL | 媒体资源状态枚举名称：`UPLOADING`=上传中，`AVAILABLE`=已就绪待绑定，`BOUND`=已绑定，`PURGING`=清理重试中 |
| cleanup_at | TIMESTAMPTZ | — | 计划清理时间（UTC 带时区时间戳），已绑定资源为空 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_media_assets | 主键唯一 | id | 按主键加锁更新状态（上传确认、绑定检查与删除），以及被规格表和业务主表外键引用 |
| idx_media_assets_cleanup_at_id | 部分复合索引 | `cleanup_at, id` (`WHERE cleanup_at IS NOT NULL`) | 后台定时清理任务按 `cleanup_at <= now` 升序、`id` 升序摘取到期未绑定或重试中的媒体资源 |

**关系**

| 关系 | 目标表 | 外键字段 | 说明 |
| --- | --- | --- | --- |
| 1:N | `media_variants` | `media_variants.asset_id` | 一个媒体资源对应多条交付规格记录，删除主表记录时级联删除所属规格（`ON DELETE CASCADE`） |
| 1:1 | `artists` | `artists.avatar_asset_id` | 艺人头像引用媒体资源，存在业务引用时禁止直接删除资源（`ON DELETE RESTRICT`） |

**建表 SQL**

```sql
CREATE TABLE media_assets (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    cleanup_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_media_assets PRIMARY KEY (id)
);
CREATE INDEX idx_media_assets_cleanup_at_id ON media_assets (cleanup_at, id)
WHERE cleanup_at IS NOT NULL;
COMMENT ON TABLE media_assets IS '媒体资源主表，记录媒体类型、生命周期状态与到期清理时间';
COMMENT ON COLUMN media_assets.id IS '主键 ID';
COMMENT ON COLUMN media_assets.kind IS '媒体资源类型枚举名称：IMAGE=图片';
COMMENT ON COLUMN media_assets.status IS '媒体资源状态枚举名称：UPLOADING=上传中，AVAILABLE=已就绪待绑定，BOUND=已绑定，PURGING=清理重试中';
COMMENT ON COLUMN media_assets.cleanup_at IS '计划清理时间（UTC 带时区时间戳），已绑定资源为空';
COMMENT ON COLUMN media_assets.created_at IS '创建时间（UTC 带时区时间戳）';
COMMENT ON COLUMN media_assets.updated_at IS '最后更新时间（UTC 带时区时间戳）';
```

## media_variants 表

媒体交付规格表，记录同一媒体资源下各输出规格的存储定位与元数据。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明（枚举须写全映射） |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| asset_id | BIGINT | FK(`media_assets.id` ON DELETE CASCADE), NOT NULL | 所属媒体资源 ID |
| code | VARCHAR(32) | NOT NULL | 交付规格枚举名称：`BASE`=基础原图规格 |
| storage_space | VARCHAR(32) | NOT NULL | 逻辑存储空间枚举名称：`PUBLIC_MEDIA`=公开媒体空间 |
| object_key | VARCHAR(1024) | NOT NULL | 对象存储键 |
| content_type | VARCHAR(64) | NOT NULL | 实际输出 MIME 类型，如 `image/jpeg`、`image/png` |
| byte_size | BIGINT | NOT NULL, CHECK(`byte_size > 0`) | 文件字节大小，必须为正数 |
| width | INTEGER | CHECK(`(width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)`) | 输出图像宽度（像素），与 `height` 同为空或同为正数 |
| height | INTEGER | CHECK(`(width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)`) | 输出图像高度（像素），与 `width` 同为空或同为正数 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_media_variants | 主键唯一 | id | 交付规格记录主键标识 |
| uk_media_variants_asset_id_code | 唯一复合约束 | `asset_id, code` | 保证同一媒体资源下每种交付规格唯一，并支撑绑定时按 `asset_id` 与 `code='BASE'` 精准读取规格及对象键 |
| uk_media_variants_storage_space_object_key | 唯一复合约束 | `storage_space, object_key` | 防止同一逻辑存储空间内不同规格记录指向同一对象键，避免误删共享对象 |

**关系**

| 关系 | 目标表 | 外键字段 | 说明 |
| --- | --- | --- | --- |
| N:1 | `media_assets` | `asset_id` | 所属媒体资源，主表记录删除时级联删除规格记录（`ON DELETE CASCADE`） |

**建表 SQL**

```sql
CREATE TABLE media_variants (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    asset_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    storage_space VARCHAR(32) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_media_variants PRIMARY KEY (id),
    CONSTRAINT fk_media_variants_media_assets FOREIGN KEY (asset_id) REFERENCES media_assets (id) ON DELETE CASCADE,
    CONSTRAINT uk_media_variants_asset_id_code UNIQUE (asset_id, code),
    CONSTRAINT uk_media_variants_storage_space_object_key UNIQUE (storage_space, object_key),
    CONSTRAINT ck_media_variants_byte_size CHECK (byte_size > 0),
    CONSTRAINT ck_media_variants_dimensions CHECK (
        (
            width IS NULL
            AND height IS NULL
        )
        OR (
            width > 0
            AND height > 0
        )
    )
);
COMMENT ON TABLE media_variants IS '媒体交付规格表，记录同一媒体资源下各输出规格的存储定位与元数据';
COMMENT ON COLUMN media_variants.id IS '主键 ID';
COMMENT ON COLUMN media_variants.asset_id IS '所属媒体资源 ID（引用 media_assets.id）';
COMMENT ON COLUMN media_variants.code IS '交付规格枚举名称：BASE=基础原图规格';
COMMENT ON COLUMN media_variants.storage_space IS '逻辑存储空间枚举名称：PUBLIC_MEDIA=公开媒体空间';
COMMENT ON COLUMN media_variants.object_key IS '对象存储键';
COMMENT ON COLUMN media_variants.content_type IS '实际输出 MIME 类型，如 image/jpeg、image/png';
COMMENT ON COLUMN media_variants.byte_size IS '文件字节大小，必须为正数';
COMMENT ON COLUMN media_variants.width IS '输出图像宽度（像素），与 height 同为空或同为正数';
COMMENT ON COLUMN media_variants.height IS '输出图像高度（像素），与 width 同为空或同为正数';
COMMENT ON COLUMN media_variants.created_at IS '创建时间（UTC 带时区时间戳）';
COMMENT ON COLUMN media_variants.updated_at IS '最后更新时间（UTC 带时区时间戳）';
```
