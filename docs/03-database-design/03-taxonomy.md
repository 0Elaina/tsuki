# 分类与标签模块数据库设计

## categories 表

曲库分区表，保存分区名称、排序值、状态与时间戳。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明（枚举须写全映射） |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| name | VARCHAR(64) | NOT NULL, CHECK(`name <> '' AND name = btrim(name)`) | 分区展示名称，保留原始大小写且大小写不敏感唯一 |
| sort_order | INTEGER | NOT NULL, DEFAULT `0`, CHECK(`sort_order >= 0`) | 排序值，非负整数，数值越小越靠前，允许相同值 |
| status | VARCHAR(16) | NOT NULL | 分区状态枚举名称：`ACTIVE`=启用，`DISABLED`=停用 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_categories | 主键唯一 | id | `CategoryMapper.selectById` 按主键查询分区详情，以及后续业务表关联引用 |
| uk_categories_name | 唯一表达式索引 | `lower(name)` | `CategoryServiceImpl.create` 新增或改名时防止同表大小写不敏感重名（如 `Rock` 与 `rock`） |
| idx_categories_sort_order_id | 普通复合索引 | `sort_order, id` | 服务后续管理端与用户端按排序值升序、ID 升序的分页与列表查询 |

**关系**

当前表不持有外键，也暂未被其他表外键引用；后续曲库歌曲关联表将引用本表 `id`。

**建表 SQL**

```sql
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
```

## tags 表

曲库标签表，保存标签名称、排序值、状态与时间戳。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明（枚举须写全映射） |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| name | VARCHAR(64) | NOT NULL, CHECK(`name <> '' AND name = btrim(name)`) | 标签展示名称，保留原始大小写且大小写不敏感唯一 |
| sort_order | INTEGER | NOT NULL, DEFAULT `0`, CHECK(`sort_order >= 0`) | 排序值，非负整数，数值越小越靠前，允许相同值 |
| status | VARCHAR(16) | NOT NULL | 标签状态枚举名称：`ACTIVE`=启用，`DISABLED`=停用 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_tags | 主键唯一 | id | 标签按主键查询详情以及后续业务表关联引用 |
| uk_tags_name | 唯一表达式索引 | `lower(name)` | 新增或改名时防止同表大小写不敏感重名 |
| idx_tags_sort_order_id | 普通复合索引 | `sort_order, id` | 服务后续管理端与用户端按排序值升序、ID 升序的分页与列表查询 |

**关系**

当前表不持有外键，也暂未被其他表外键引用；后续曲库歌曲与标签多对多关联表将引用本表 `id`。

**建表 SQL**

```sql
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
```
