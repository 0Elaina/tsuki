# 艺人模块数据库设计

## artists 表

艺人档案表，保存艺人展示名、排序名、类型、地区代码、区分说明、简介与时间戳。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明（枚举须写全映射） |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| name | VARCHAR(256) | NOT NULL, CHECK(`name <> '' AND name = btrim(name)`) | 艺人展示名，保留录入大小写，允许同名档案 |
| sort_name | VARCHAR(256) | CHECK(`sort_name IS NULL OR (sort_name <> '' AND sort_name = btrim(sort_name))`) | 排序名，空值时以展示名参与排序 |
| type | VARCHAR(16) | NOT NULL | 艺人类型枚举名称：`PERSON`=个人，`GROUP`=团体，`UNKNOWN`=未知 |
| country_code | VARCHAR(2) | CHECK(`country_code IS NULL OR country_code COLLATE "C" ~ '^[A-Z]{2}$'`) | 主要关联国家或地区代码（ISO 3166-1 alpha-2 大写） |
| disambiguation | VARCHAR(256) | — | 用于区分同名艺人的短说明 |
| biography | TEXT | — | 艺人纯文本简介 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_artists | 主键唯一 | id | 按主键查询艺人详情，以及别名表与后续曲库署名表关联引用 |
| idx_artists_lower_sort_name_id | 普通表达式复合索引 | `lower(coalesce(sort_name, name)), id` | 服务管理端艺人列表与候选人查询按 `lower(coalesce(sort_name, name))` 升序、同值按 `id` 升序稳定排序 |

**关系**

| 关系 | 目标表 | 外键字段 | 说明 |
| --- | --- | --- | --- |
| 1:N | `artist_aliases` | `artist_aliases.artist_id` | 一位艺人可拥有多条有序替代名称，物理删除艺人时级联删除所属别名 |

**建表 SQL**

```sql
CREATE TABLE artists (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    name VARCHAR(256) NOT NULL,
    sort_name VARCHAR(256),
    type VARCHAR(16) NOT NULL,
    country_code VARCHAR(2),
    disambiguation VARCHAR(256),
    biography TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_artists PRIMARY KEY (id),
    CONSTRAINT ck_artists_name CHECK (
        name <> ''
        AND name = btrim(name)
    ),
    CONSTRAINT ck_artists_sort_name CHECK (
        sort_name IS NULL
        OR (
            sort_name <> ''
            AND sort_name = btrim(sort_name)
        )
    ),
    CONSTRAINT ck_artists_country_code CHECK (
        country_code IS NULL
        OR country_code COLLATE "C" ~ '^[A-Z]{2}$'
    )
);
CREATE INDEX idx_artists_lower_sort_name_id ON artists (lower(coalesce(sort_name, name)), id);
COMMENT ON TABLE artists IS '艺人档案表，保存艺人名称、排序名、类型、地区与简介';
COMMENT ON COLUMN artists.id IS '主键 ID';
COMMENT ON COLUMN artists.name IS '艺人展示名，保留录入大小写，允许同名档案';
COMMENT ON COLUMN artists.sort_name IS '排序名，空值时以展示名参与排序';
COMMENT ON COLUMN artists.type IS '艺人类型枚举名称：PERSON=个人，GROUP=团体，UNKNOWN=未知';
COMMENT ON COLUMN artists.country_code IS '主要关联国家或地区代码（ISO 3166-1 alpha-2 大写）';
COMMENT ON COLUMN artists.disambiguation IS '用于区分同名艺人的短说明';
COMMENT ON COLUMN artists.biography IS '艺人纯文本简介';
COMMENT ON COLUMN artists.created_at IS '创建时间（UTC 带时区时间戳）';
COMMENT ON COLUMN artists.updated_at IS '最后更新时间（UTC 带时区时间戳）';
```

## artist_aliases 表

艺人别名表，保存艺人的有序替代名称。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明（枚举须写全映射） |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| artist_id | BIGINT | FK(`artists.id` ON DELETE CASCADE), NOT NULL | 所属艺人 ID |
| name | VARCHAR(256) | NOT NULL, CHECK(`name <> '' AND name = btrim(name)`) | 艺人别名，保留录入大小写且同艺人下大小写不敏感唯一 |
| position | INTEGER | NOT NULL, CHECK(`position >= 0`) | 从 0 开始的展示顺序位置 |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_artist_aliases | 主键唯一 | id | 别名记录主键标识 |
| uk_artist_aliases_artist_id_position | 唯一复合约束 | `artist_id, position` | 保证同一艺人每个顺序位置唯一，并支撑按艺人读取别名且按 `position` 排序 |
| uk_artist_aliases_artist_id_name | 唯一表达式索引 | `artist_id, lower(name)` | 防止同一艺人重复录入大小写变体别名，并支撑后续候选人查询的精确别名匹配 |

**关系**

| 关系 | 目标表 | 外键字段 | 说明 |
| --- | --- | --- | --- |
| N:1 | `artists` | `artist_id` | 所属艺人，主表记录删除时级联删除别名（`ON DELETE CASCADE`） |

**建表 SQL**

```sql
CREATE TABLE artist_aliases (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    artist_id BIGINT NOT NULL,
    name VARCHAR(256) NOT NULL,
    position INTEGER NOT NULL,
    CONSTRAINT pk_artist_aliases PRIMARY KEY (id),
    CONSTRAINT fk_artist_aliases_artists FOREIGN KEY (artist_id) REFERENCES artists (id) ON DELETE CASCADE,
    CONSTRAINT uk_artist_aliases_artist_id_position UNIQUE (artist_id, position),
    CONSTRAINT ck_artist_aliases_name CHECK (
        name <> ''
        AND name = btrim(name)
    ),
    CONSTRAINT ck_artist_aliases_position CHECK (position >= 0)
);
CREATE UNIQUE INDEX uk_artist_aliases_artist_id_name ON artist_aliases (artist_id, lower(name));
COMMENT ON TABLE artist_aliases IS '艺人别名表，保存艺人的有序替代名称';
COMMENT ON COLUMN artist_aliases.id IS '主键 ID';
COMMENT ON COLUMN artist_aliases.artist_id IS '所属艺人 ID（关联 artists.id）';
COMMENT ON COLUMN artist_aliases.name IS '艺人别名，保留录入大小写且同艺人下大小写不敏感唯一';
COMMENT ON COLUMN artist_aliases.position IS '从 0 开始的展示顺序位置';
```
