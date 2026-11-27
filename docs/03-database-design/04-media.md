# 媒体模块数据库设计

## media_assets 表

媒体源资产表，保存浏览器直传建档、声明约束、源对象存储定位、确认状态及源对象 ETag。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| uploader_account_id | BIGINT | FK, NOT NULL | 签发与上传所属账号 ID，外键引用 `user_accounts.id` |
| kind | VARCHAR(16) | NOT NULL | 媒体类型枚举名称：`IMAGE`=图片 |
| status | VARCHAR(32) | NOT NULL | 资产状态枚举名称：`PENDING_UPLOAD`=待确认上传，`UPLOAD_CONFIRMED`=已确认上传待处理，`READY`=原件已校验归档可用，`PROCESSING_FAILED`=异步处理失败 |
| object_key | VARCHAR(1024) | UNIQUE, NOT NULL | 源对象在对象存储中的完整 S3 键（暂存期为 `incoming/image/{UTC年}/{UTC月}/{UTC日}/{uploadId}/source`，归档就绪后切换为 `assets/image/{assetId}/original-{jobId}-{attemptCount}.{ext}`） |
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

## media_jobs 表

媒体异步处理任务与 Outbox 调度表，保存任务执行进度、批量派发领取标记、执行租期及最近一次失败原因。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| asset_id | BIGINT | FK, NOT NULL | 关联媒体资产 ID，外键引用 `media_assets.id`（`ON DELETE RESTRICT`） |
| type | VARCHAR(32) | NOT NULL | 任务类型枚举名称，当前为 `PROCESS_IMAGE`（图片验证与原件归档主任务） |
| status | VARCHAR(16) | NOT NULL | 任务状态枚举名称：`PENDING`=待执行/待重排，`RUNNING`=执行中，`SUCCEEDED`=已成功，`FAILED`=终态失败 |
| attempt_count | INTEGER | NOT NULL, CHECK (`attempt_count >= 0`) | 已领取执行次数，消费端抢占执行时递增 |
| available_at | TIMESTAMPTZ | NOT NULL | 最早可执行时间（UTC 带时区时间戳），失败重排时按退避延迟推后 |
| lease_until | TIMESTAMPTZ | 可空 | 本次执行的租期截止时间（UTC 带时区时间戳），仅 `RUNNING` 期间非空，终态或重排时清空 |
| dispatch_after | TIMESTAMPTZ | 可空 | 最早可派发或重领时间（UTC 带时区时间戳），确认投递、进入执行或终态时清空 |
| dispatch_token | VARCHAR(36) | 可空 | 当前派发批次的 UUID 字符串领取标识，确认投递、进入执行或终态时清空 |
| dispatched_at | TIMESTAMPTZ | 可空 | 本轮发布确认或消费领取时间（UTC 带时区时间戳），重试回退 `PENDING` 时清空 |
| failure_code | VARCHAR(64) | 可空 | 最近一次执行失败原因码（如 `SOURCE_MISSING`、`SOURCE_CHANGED`、`IMAGE_TOO_LARGE`、`INVALID_IMAGE`、`STORAGE_ERROR`、`LEASE_EXPIRED`），成功时清空 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 记录创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 记录最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_media_jobs | 主键唯一 | id | 消费端按任务主键原子抢占、状态推进及批量确认回写定位 |
| idx_media_jobs_asset_id | 普通 | asset_id | 支撑 `fk_media_jobs_media_assets` 外键删除限制检查（`ON DELETE RESTRICT`）及按资产查询关联处理任务 |
| idx_media_jobs_dispatch_after | 部分索引 (`WHERE dispatch_after IS NOT NULL`) | dispatch_after, id | 支撑 `MediaJobDispatcher` 按 `dispatch_after, id` 顺序跳过锁批量领取待派发或需重领的任务，终态与已确认投递记录清空该列自动退出索引 |
| idx_media_jobs_lease_until | 部分索引 (`WHERE lease_until IS NOT NULL`) | lease_until, id | 支撑 `MediaJobReaper` 按 `lease_until, id` 顺序跳过锁扫描并回收租期已过期的 `RUNNING` 任务，非运行态清空该列自动退出索引 |

**关系**

| 关系 | 目标表 | 外键字段 | 说明 |
| --- | --- | --- | --- |
| N:1 | media_assets | asset_id | 任务归属的媒体源资产，外键约束 `ON DELETE RESTRICT` |

**建表 SQL**

```sql
CREATE TABLE media_jobs (
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
    failure_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_media_jobs PRIMARY KEY (id),
    CONSTRAINT fk_media_jobs_media_assets FOREIGN KEY (asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
    CONSTRAINT ck_media_jobs_attempt_count CHECK (attempt_count >= 0)
);
CREATE INDEX idx_media_jobs_asset_id ON media_jobs (asset_id);
CREATE INDEX idx_media_jobs_dispatch_after ON media_jobs (dispatch_after, id)
WHERE dispatch_after IS NOT NULL;
CREATE INDEX idx_media_jobs_lease_until ON media_jobs (lease_until, id)
WHERE lease_until IS NOT NULL;
COMMENT ON TABLE media_jobs IS '媒体处理任务表';
COMMENT ON COLUMN media_jobs.id IS '主键 ID';
COMMENT ON COLUMN media_jobs.asset_id IS '媒体资产 ID';
COMMENT ON COLUMN media_jobs.type IS '任务类型';
COMMENT ON COLUMN media_jobs.status IS '任务状态';
COMMENT ON COLUMN media_jobs.attempt_count IS '已领取执行次数';
COMMENT ON COLUMN media_jobs.available_at IS '最早可执行时间';
COMMENT ON COLUMN media_jobs.lease_until IS '执行占用截止时间';
COMMENT ON COLUMN media_jobs.dispatch_after IS '最早可派发或重领时间';
COMMENT ON COLUMN media_jobs.dispatch_token IS '投递批次领取标识';
COMMENT ON COLUMN media_jobs.dispatched_at IS '投递确认或消费领取时间';
COMMENT ON COLUMN media_jobs.failure_code IS '最近一次执行失败原因';
COMMENT ON COLUMN media_jobs.created_at IS '创建时间';
COMMENT ON COLUMN media_jobs.updated_at IS '更新时间';
```

## media_variants 表

媒体交付规格表，保存按需生成的各具名派生规格对象定位、实际编码格式、尺寸及扩展元数据。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| asset_id | BIGINT | FK, NOT NULL | 关联媒体资产 ID，外键引用 `media_assets.id`（`ON DELETE RESTRICT`） |
| code | VARCHAR(32) | NOT NULL | 规格编码枚举名称，当前图片原图级预设为 `FULL`（后续按业务扩展头像、封面等具名预设） |
| object_key | VARCHAR(1024) | UNIQUE, NOT NULL | 派生规格在对象存储中的完整 S3 键（如 `assets/image/{assetId}/variants/{fileStem}-{versionId}.{ext}`） |
| content_type | VARCHAR(64) | NOT NULL | 实际输出编码 MIME 类型（如 `image/jpeg` 或 `image/png`） |
| byte_size | BIGINT | NOT NULL, CHECK (`byte_size > 0`) | 规格产物字节大小 |
| width | INTEGER | 可空, CHECK (`width > 0`) | 图像实际宽度（像素，EXIF 方向校正后），非图像媒体可空 |
| height | INTEGER | 可空, CHECK (`height > 0`) | 图像实际高度（像素，EXIF 方向校正后），非图像媒体可空 |
| metadata | JSONB | 可空 | 规格扩展元数据（JSONB），Java 侧通过 `PostgresJsonbTypeHandler` 映射为 `Map<String, Object>` |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 记录创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 记录最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_media_variants | 主键唯一 | id | 按规格主键定位单条规格记录 |
| uk_media_variants_asset_id_code | 唯一 | asset_id, code | 保证同一媒体资产下每个规格编码唯一，同时以前导列 `asset_id` 支撑按资产查询规格及 `fk_media_variants_media_assets` 外键删除限制检查 |
| uk_media_variants_object_key | 唯一 | object_key | 保证规格对象键与规格记录一一对应，防止重复写入或误覆盖 |

**关系**

| 关系 | 目标表 | 外键字段 | 说明 |
| --- | --- | --- | --- |
| N:1 | media_assets | asset_id | 派生规格归属的媒体源资产，外键约束 `ON DELETE RESTRICT` |

**建表 SQL**

```sql
CREATE TABLE media_variants (
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
    CONSTRAINT pk_media_variants PRIMARY KEY (id),
    CONSTRAINT uk_media_variants_asset_id_code UNIQUE (asset_id, code),
    CONSTRAINT uk_media_variants_object_key UNIQUE (object_key),
    CONSTRAINT fk_media_variants_media_assets FOREIGN KEY (asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
    CONSTRAINT ck_media_variants_byte_size CHECK (byte_size > 0),
    CONSTRAINT ck_media_variants_width CHECK (width > 0),
    CONSTRAINT ck_media_variants_height CHECK (height > 0)
);
COMMENT ON TABLE media_variants IS '媒体交付规格表';
COMMENT ON COLUMN media_variants.id IS '主键 ID';
COMMENT ON COLUMN media_variants.asset_id IS '媒体资产 ID';
COMMENT ON COLUMN media_variants.code IS '规格编码';
COMMENT ON COLUMN media_variants.object_key IS '对象键';
COMMENT ON COLUMN media_variants.content_type IS '内容类型';
COMMENT ON COLUMN media_variants.byte_size IS '字节大小';
COMMENT ON COLUMN media_variants.width IS '宽度（像素）';
COMMENT ON COLUMN media_variants.height IS '高度（像素）';
COMMENT ON COLUMN media_variants.metadata IS '规格扩展元数据';
COMMENT ON COLUMN media_variants.created_at IS '创建时间';
COMMENT ON COLUMN media_variants.updated_at IS '更新时间';
```
