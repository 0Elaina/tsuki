CREATE TABLE artists (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    name VARCHAR(128) NOT NULL,
    sort_name VARCHAR(128),
    type VARCHAR(16) NOT NULL,
    country_code VARCHAR(2),
    disambiguation VARCHAR(128),
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
CREATE TABLE artist_aliases (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    artist_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
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