CREATE TABLE file_variants (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    asset_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    preset_revision INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    storage_scope VARCHAR(16) NOT NULL,
    object_key VARCHAR(1024),
    content_type VARCHAR(128),
    byte_size BIGINT,
    etag VARCHAR(256),
    metadata JSONB,
    failure_code VARCHAR(64),
    failure_message TEXT,
    withdrawn_at TIMESTAMPTZ,
    purged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_file_variants PRIMARY KEY (id),
    CONSTRAINT uk_file_variants_asset_id_id UNIQUE (asset_id, id),
    CONSTRAINT fk_file_variants_file_assets FOREIGN KEY (asset_id) REFERENCES file_assets (id) ON DELETE RESTRICT,
    CONSTRAINT ck_file_variants_preset_revision CHECK (preset_revision > 0),
    CONSTRAINT ck_file_variants_byte_size CHECK (byte_size IS NULL OR byte_size > 0)
);
CREATE UNIQUE INDEX uk_file_variants_active_spec ON file_variants (asset_id, code, preset_revision, storage_scope)
WHERE status <> 'WITHDRAWN';
CREATE UNIQUE INDEX uk_file_variants_storage_scope_object_key ON file_variants (storage_scope, object_key)
WHERE object_key IS NOT NULL;
COMMENT ON TABLE file_variants IS '文件衍生物规格表';
COMMENT ON COLUMN file_variants.id IS '主键 ID';
COMMENT ON COLUMN file_variants.asset_id IS '文件资产 ID';
COMMENT ON COLUMN file_variants.code IS '规格编码';
COMMENT ON COLUMN file_variants.preset_revision IS '规格预设修订版本号';
COMMENT ON COLUMN file_variants.status IS '衍生物状态';
COMMENT ON COLUMN file_variants.storage_scope IS '逻辑存储范围';
COMMENT ON COLUMN file_variants.object_key IS '衍生物对象键';
COMMENT ON COLUMN file_variants.content_type IS '输出内容类型';
COMMENT ON COLUMN file_variants.byte_size IS '字节大小';
COMMENT ON COLUMN file_variants.etag IS '衍生物对象 ETag';
COMMENT ON COLUMN file_variants.metadata IS '衍生物扩展元数据（含图片宽高）';
COMMENT ON COLUMN file_variants.failure_code IS '最近一次生成失败原因码';
COMMENT ON COLUMN file_variants.failure_message IS '最近一次生成失败详情';
COMMENT ON COLUMN file_variants.withdrawn_at IS '撤回时间';
COMMENT ON COLUMN file_variants.purged_at IS '对象存储物理删除完成时间';
COMMENT ON COLUMN file_variants.created_at IS '创建时间';
COMMENT ON COLUMN file_variants.updated_at IS '更新时间';

CREATE TABLE file_jobs (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    asset_id BIGINT NOT NULL,
    variant_id BIGINT,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    dispatch_after TIMESTAMPTZ,
    dispatch_token VARCHAR(36),
    dispatched_at TIMESTAMPTZ,
    dedup_key VARCHAR(128),
    payload JSONB,
    failure_code VARCHAR(64),
    failure_message TEXT,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_file_jobs PRIMARY KEY (id),
    CONSTRAINT fk_file_jobs_file_assets FOREIGN KEY (asset_id) REFERENCES file_assets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_file_jobs_file_variants FOREIGN KEY (asset_id, variant_id) REFERENCES file_variants (asset_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_file_jobs_attempt_count CHECK (attempt_count >= 0)
);
CREATE INDEX idx_file_jobs_asset_id ON file_jobs (asset_id);
CREATE INDEX idx_file_jobs_asset_variant ON file_jobs (asset_id, variant_id)
WHERE variant_id IS NOT NULL;
CREATE INDEX idx_file_jobs_dispatch_after ON file_jobs (dispatch_after, id)
WHERE status = 'PENDING' AND dispatch_after IS NOT NULL;
CREATE INDEX idx_file_jobs_lease_until ON file_jobs (lease_until, id)
WHERE status = 'RUNNING' AND lease_until IS NOT NULL;
CREATE UNIQUE INDEX uk_file_jobs_active_dedup_key ON file_jobs (dedup_key)
WHERE dedup_key IS NOT NULL AND status IN ('PENDING', 'RUNNING');
CREATE INDEX idx_file_jobs_finished_at ON file_jobs (finished_at, id)
WHERE finished_at IS NOT NULL;
COMMENT ON TABLE file_jobs IS '文件处理任务表';
COMMENT ON COLUMN file_jobs.id IS '主键 ID';
COMMENT ON COLUMN file_jobs.asset_id IS '文件资产 ID';
COMMENT ON COLUMN file_jobs.variant_id IS '关联衍生物 ID（生成与清理衍生物任务必填）';
COMMENT ON COLUMN file_jobs.type IS '任务类型';
COMMENT ON COLUMN file_jobs.status IS '任务状态';
COMMENT ON COLUMN file_jobs.attempt_count IS '已领取执行次数';
COMMENT ON COLUMN file_jobs.available_at IS '最早可执行时间';
COMMENT ON COLUMN file_jobs.lease_until IS '执行占用截止时间';
COMMENT ON COLUMN file_jobs.dispatch_after IS '最早可派发或补派时间';
COMMENT ON COLUMN file_jobs.dispatch_token IS '投递批次领取标识';
COMMENT ON COLUMN file_jobs.dispatched_at IS '投递确认或消费领取时间';
COMMENT ON COLUMN file_jobs.dedup_key IS '活跃任务防重键';
COMMENT ON COLUMN file_jobs.payload IS '任务扩展参数';
COMMENT ON COLUMN file_jobs.failure_code IS '最近一次执行失败原因码';
COMMENT ON COLUMN file_jobs.failure_message IS '最近一次执行失败详情';
COMMENT ON COLUMN file_jobs.finished_at IS '任务进入终态完成时间';
COMMENT ON COLUMN file_jobs.created_at IS '创建时间';
COMMENT ON COLUMN file_jobs.updated_at IS '更新时间';
