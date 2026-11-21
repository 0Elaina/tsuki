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
ALTER TABLE artists DROP CONSTRAINT ck_artists_country_code,
    DROP COLUMN country_code,
    DROP COLUMN disambiguation,
    DROP COLUMN biography,
    ADD COLUMN avatar_asset_id BIGINT,
    ADD COLUMN avatar_url VARCHAR(1025),
    ADD CONSTRAINT uk_artists_avatar_asset_id UNIQUE (avatar_asset_id),
    ADD CONSTRAINT fk_artists_media_assets FOREIGN KEY (avatar_asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_artists_avatar_pair CHECK (
        (
            avatar_asset_id IS NULL
            AND avatar_url IS NULL
        )
        OR (
            avatar_asset_id IS NOT NULL
            AND avatar_url IS NOT NULL
        )
    ),
    ADD CONSTRAINT ck_artists_avatar_url CHECK (
        avatar_url IS NULL
        OR (
            avatar_url LIKE '/%'
            AND avatar_url = btrim(avatar_url)
        )
    );
COMMENT ON TABLE artists IS '艺人档案表，保存艺人名称、排序名、类型、头像引用与时间戳';
COMMENT ON COLUMN artists.type IS '艺人类型枚举名称：PERSON=个人，GROUP=团体，VIRTUAL=虚拟艺人，UNKNOWN=未知';
COMMENT ON COLUMN artists.avatar_asset_id IS '艺人头像关联的媒体资源 ID（引用 media_assets.id）';
COMMENT ON COLUMN artists.avatar_url IS '艺人头像相对访问路径，由对象键拼接前导斜杠生成';
CREATE TABLE artist_profiles (
    artist_id BIGINT NOT NULL,
    country_code VARCHAR(2),
    biography TEXT,
    debut_year SMALLINT,
    official_website_url VARCHAR(2048),
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