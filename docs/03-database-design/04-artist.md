# 艺人模块数据库设计

## artists 表

艺人档案表，保存艺人展示名、排序名、类型、头像引用与时间戳。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明（枚举须写全映射） |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| name | VARCHAR(256) | NOT NULL, CHECK(`name <> '' AND name = btrim(name)`) | 艺人展示名，保留录入大小写，允许同名档案 |
| sort_name | VARCHAR(256) | CHECK(`sort_name IS NULL OR (sort_name <> '' AND sort_name = btrim(sort_name))`) | 排序名，空值时以展示名参与排序 |
| type | VARCHAR(16) | NOT NULL | 艺人类型枚举名称：`PERSON`=个人，`GROUP`=团体，`VIRTUAL`=虚拟艺人，`UNKNOWN`=未知 |
| avatar_asset_id | BIGINT | UNIQUE, FK(`media_assets.id` ON DELETE RESTRICT) | 艺人头像关联的媒体资源 ID，与 `avatar_url` 同为空或同非空 |
| avatar_url | VARCHAR(1025) | CHECK(`avatar_url IS NULL OR (avatar_url LIKE '/%' AND avatar_url = btrim(avatar_url))`) | 艺人头像相对访问路径，由对象键拼接前导斜杠生成，与 `avatar_asset_id` 同为空或同非空 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_artists | 主键唯一 | id | 按主键查询艺人详情，以及资料表、别名表与后续曲库署名表关联引用 |
| uk_artists_avatar_asset_id | 唯一约束 | avatar_asset_id | 保证单个媒体资源最多只能绑定为一位艺人的头像 |
| idx_artists_lower_sort_name_id | 普通表达式复合索引 | `lower(coalesce(sort_name, name)), id` | 服务管理端艺人列表与候选人查询按 `lower(coalesce(sort_name, name))` 升序、同值按 `id` 升序稳定排序 |

**关系**

| 关系 | 目标表 | 外键字段 | 说明 |
| --- | --- | --- | --- |
| 1:1 | `media_assets` | `avatar_asset_id` | 艺人头像引用的媒体资源，被引用期间禁止物理删除资源（`ON DELETE RESTRICT`） |
| 1:1 | `artist_profiles` | `artist_profiles.artist_id` | 艺人扩展资料，存在任意扩展资料字段时按需写入，物理删除艺人时级联删除（`ON DELETE CASCADE`） |
| 1:N | `artist_aliases` | `artist_aliases.artist_id` | 一位艺人可拥有多条有序替代名称，物理删除艺人时级联删除所属别名（`ON DELETE CASCADE`） |

**建表 SQL**

```sql
CREATE TABLE artists (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    name VARCHAR(256) NOT NULL,
    sort_name VARCHAR(256),
    type VARCHAR(16) NOT NULL,
    avatar_asset_id BIGINT,
    avatar_url VARCHAR(1025),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_artists PRIMARY KEY (id),
    CONSTRAINT uk_artists_avatar_asset_id UNIQUE (avatar_asset_id),
    CONSTRAINT fk_artists_media_assets FOREIGN KEY (avatar_asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
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
    CONSTRAINT ck_artists_avatar_pair CHECK (
        (
            avatar_asset_id IS NULL
            AND avatar_url IS NULL
        )
        OR (
            avatar_asset_id IS NOT NULL
            AND avatar_url IS NOT NULL
        )
    ),
    CONSTRAINT ck_artists_avatar_url CHECK (
        avatar_url IS NULL
        OR (
            avatar_url LIKE '/%'
            AND avatar_url = btrim(avatar_url)
        )
    )
);
CREATE INDEX idx_artists_lower_sort_name_id ON artists (lower(coalesce(sort_name, name)), id);
COMMENT ON TABLE artists IS '艺人档案表，保存艺人名称、排序名、类型、头像引用与时间戳';
COMMENT ON COLUMN artists.id IS '主键 ID';
COMMENT ON COLUMN artists.name IS '艺人展示名，保留录入大小写，允许同名档案';
COMMENT ON COLUMN artists.sort_name IS '排序名，空值时以展示名参与排序';
COMMENT ON COLUMN artists.type IS '艺人类型枚举名称：PERSON=个人，GROUP=团体，VIRTUAL=虚拟艺人，UNKNOWN=未知';
COMMENT ON COLUMN artists.avatar_asset_id IS '艺人头像关联的媒体资源 ID（引用 media_assets.id）';
COMMENT ON COLUMN artists.avatar_url IS '艺人头像相对访问路径，由对象键拼接前导斜杠生成';
COMMENT ON COLUMN artists.created_at IS '创建时间（UTC 带时区时间戳）';
COMMENT ON COLUMN artists.updated_at IS '最后更新时间（UTC 带时区时间戳）';
```

> 注：历史迁移 `V5__create_artists.sql` 保持不可变；经 `V6__create_media_and_refactor_artists.sql` 演进后，已从主表移除 `country_code`、`disambiguation`、`biography` 并新增头像两列（本地已确认无存量数据需要迁移）。

## artist_profiles 表

艺人扩展资料表，按需保存地区、简介、出道年份与官方网站（四项至少一项非空时才写入）。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明（枚举须写全映射） |
| --- | --- | --- | --- |
| artist_id | BIGINT | PK, FK(`artists.id` ON DELETE CASCADE), NOT NULL | 所属艺人 ID |
| country_code | VARCHAR(2) | CHECK(`country_code IS NULL OR country_code COLLATE "C" ~ '^[A-Z]{2}$'`) | 主要关联国家或地区代码（ISO 3166-1 alpha-2 大写） |
| biography | TEXT | CHECK(`biography IS NULL OR biography <> ''`) | 艺人纯文本简介 |
| debut_year | SMALLINT | CHECK(`debut_year IS NULL OR debut_year BETWEEN 1 AND 9999`) | 出道年份（1 至 9999） |
| official_website_url | VARCHAR(2048) | CHECK(`official_website_url IS NULL OR official_website_url <> ''`) | 艺人官方网站 HTTP/HTTPS 地址 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_artist_profiles | 主键唯一 | artist_id | 按艺人主键 1:1 关联查询或更新扩展资料 |

**关系**

| 关系 | 目标表 | 外键字段 | 说明 |
| --- | --- | --- | --- |
| 1:1 | `artists` | `artist_id` | 所属艺人主记录，主表记录删除时级联删除扩展资料（`ON DELETE CASCADE`） |

**建表 SQL**

```sql
CREATE TABLE artist_profiles (
    artist_id BIGINT NOT NULL,
    country_code VARCHAR(2),
    biography TEXT,
    debut_year SMALLINT,
    official_website_url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_artist_profiles PRIMARY KEY (artist_id),
    CONSTRAINT fk_artist_profiles_artists FOREIGN KEY (artist_id) REFERENCES artists (id) ON DELETE CASCADE,
    CONSTRAINT ck_artist_profiles_non_empty CHECK (
        country_code IS NOT NULL
        OR biography IS NOT NULL
        OR debut_year IS NOT NULL
        OR official_website_url IS NOT NULL
    ),
    CONSTRAINT ck_artist_profiles_country_code CHECK (
        country_code IS NULL
        OR country_code COLLATE "C" ~ '^[A-Z]{2}$'
    ),
    CONSTRAINT ck_artist_profiles_biography CHECK (
        biography IS NULL
        OR biography <> ''
    ),
    CONSTRAINT ck_artist_profiles_debut_year CHECK (
        debut_year IS NULL
        OR debut_year BETWEEN 1 AND 9999
    ),
    CONSTRAINT ck_artist_profiles_official_website_url CHECK (
        official_website_url IS NULL
        OR official_website_url <> ''
    )
);
COMMENT ON TABLE artist_profiles IS '艺人扩展资料表，按需保存地区、简介、出道年份与官方网站';
COMMENT ON COLUMN artist_profiles.artist_id IS '所属艺人 ID（主键且外键引用 artists.id）';
COMMENT ON COLUMN artist_profiles.country_code IS '主要关联国家或地区代码（ISO 3166-1 alpha-2 大写）';
COMMENT ON COLUMN artist_profiles.biography IS '艺人纯文本简介';
COMMENT ON COLUMN artist_profiles.debut_year IS '出道年份（1 至 9999）';
COMMENT ON COLUMN artist_profiles.official_website_url IS '艺人官方网站 HTTP/HTTPS 地址';
COMMENT ON COLUMN artist_profiles.created_at IS '创建时间（UTC 带时区时间戳）';
COMMENT ON COLUMN artist_profiles.updated_at IS '最后更新时间（UTC 带时区时间戳）';
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
