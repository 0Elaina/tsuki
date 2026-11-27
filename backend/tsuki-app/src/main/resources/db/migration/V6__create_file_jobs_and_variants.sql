CREATE TABLE file_jobs (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    asset_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    dispatch_after TIMESTAMPTZ,
    dispatch_token VARCHAR(36),
    dispatched_at TIMESTAMPTZ,
    payload JSONB,
    failure_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_file_jobs PRIMARY KEY (id),
    CONSTRAINT fk_file_jobs_file_assets FOREIGN KEY (asset_id) REFERENCES file_assets (id) ON DELETE RESTRICT,
    CONSTRAINT ck_file_jobs_attempt_count CHECK (attempt_count >= 0)
);
CREATE INDEX idx_file_jobs_asset_id ON file_jobs (asset_id);
CREATE INDEX idx_file_jobs_dispatch_after ON file_jobs (dispatch_after, id)
WHERE dispatch_after IS NOT NULL;
CREATE INDEX idx_file_jobs_lease_until ON file_jobs (lease_until, id)
WHERE lease_until IS NOT NULL;
COMMENT ON TABLE file_jobs IS '文件处理任务表';
COMMENT ON COLUMN file_jobs.id IS '主键 ID';
COMMENT ON COLUMN file_jobs.asset_id IS '文件资产 ID';
COMMENT ON COLUMN file_jobs.type IS '任务类型';
COMMENT ON COLUMN file_jobs.status IS '任务状态';
COMMENT ON COLUMN file_jobs.attempt_count IS '已领取执行次数';
COMMENT ON COLUMN file_jobs.available_at IS '最早可执行时间';
COMMENT ON COLUMN file_jobs.lease_until IS '执行占用截止时间';
COMMENT ON COLUMN file_jobs.dispatch_after IS '最早可派发或重领时间';
COMMENT ON COLUMN file_jobs.dispatch_token IS '投递批次领取标识';
COMMENT ON COLUMN file_jobs.dispatched_at IS '投递确认或消费领取时间';
COMMENT ON COLUMN file_jobs.payload IS '任务扩展参数';
COMMENT ON COLUMN file_jobs.failure_code IS '最近一次执行失败原因';
COMMENT ON COLUMN file_jobs.created_at IS '创建时间';
COMMENT ON COLUMN file_jobs.updated_at IS '更新时间';

CREATE TABLE file_variants (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    asset_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_file_variants PRIMARY KEY (id),
    CONSTRAINT uk_file_variants_asset_id_code UNIQUE (asset_id, code),
    CONSTRAINT uk_file_variants_object_key UNIQUE (object_key),
    CONSTRAINT fk_file_variants_file_assets FOREIGN KEY (asset_id) REFERENCES file_assets (id) ON DELETE RESTRICT,
    CONSTRAINT ck_file_variants_byte_size CHECK (byte_size > 0),
    CONSTRAINT ck_file_variants_width CHECK (width > 0),
    CONSTRAINT ck_file_variants_height CHECK (height > 0)
);
COMMENT ON TABLE file_variants IS '文件交付规格表';
COMMENT ON COLUMN file_variants.id IS '主键 ID';
COMMENT ON COLUMN file_variants.asset_id IS '文件资产 ID';
COMMENT ON COLUMN file_variants.code IS '规格编码';
COMMENT ON COLUMN file_variants.object_key IS '对象键';
COMMENT ON COLUMN file_variants.content_type IS '内容类型';
COMMENT ON COLUMN file_variants.byte_size IS '字节大小';
COMMENT ON COLUMN file_variants.width IS '宽度（像素）';
COMMENT ON COLUMN file_variants.height IS '高度（像素）';
COMMENT ON COLUMN file_variants.metadata IS '规格扩展元数据';
COMMENT ON COLUMN file_variants.created_at IS '创建时间';
COMMENT ON COLUMN file_variants.updated_at IS '更新时间';
