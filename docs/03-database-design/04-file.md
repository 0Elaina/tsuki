# 文件模块数据库设计

## file_assets 表

文件资产表，保存浏览器直传建档、暂存对象定位与 ETag、归档原件定位与 ETag、业务归属绑定、未绑定宽限期、校验/处理失败诊断、原件扩展元数据及物理清理完成时间。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| uploader_account_id | BIGINT | FK, NOT NULL | 签发与上传所属账号 ID，外键引用 `user_accounts.id` |
| kind | VARCHAR(16) | NOT NULL | 文件类型枚举名称：当前为 `IMAGE`=图片（后续按业务扩展音频、歌词等类型） |
| status | VARCHAR(32) | NOT NULL | 资产状态枚举名称：`PENDING_UPLOAD`=待确认上传，`UPLOAD_CONFIRMED`=已确认上传待校验，`READY`=原件已校验归档可用，`REJECTED`=内容不合规被拒绝，`FAILED`=处理故障耗尽重试失败，`EXPIRED`=超过确认期限已过期，`DELETING`=已停发待物理清理，`DELETED`=物理对象已清理完成 |
| storage_scope | VARCHAR(16) | NOT NULL | 原件逻辑存储范围枚举名称：原件固定存放于 `PRIVATE` 私有桶 |
| staging_key | VARCHAR(1024) | UNIQUE, NOT NULL | 浏览器直传暂存区完整对象键（`staging/{kind}/{UTC年}/{UTC月}/{UTC日}/{uuid}/source`），由私有桶 7 天生命周期规则自动清理 |
| staging_etag | VARCHAR(256) | 可空 | 暂存对象 ETag 原值（保留双引号），上传确认 `HEAD` 校验通过后写入，供 Worker 条件下载与 `CopyObject` 源校验使用 |
| object_key | VARCHAR(1024) | 可空（非空时与 `storage_scope` 联合唯一） | 已校验私有原件完整对象键（`originals/{assetId}/{jobId}/{attempt}/source`），归档进入 `READY` 时固定写入 |
| content_type | VARCHAR(128) | NOT NULL | 源对象规范化小写 MIME 类型（当前图片支持 `image/jpeg`、`image/png`、`image/webp`） |
| byte_size | BIGINT | NOT NULL, CHECK (`byte_size > 0`) | 源文件精确字节大小 |
| etag | VARCHAR(256) | 可空 | 归档原件 ETag 原值（保留双引号），`CopyObject` 成功归档进入 `READY` 时写入 |
| original_filename | VARCHAR(255) | 可空 | 调用方传入并规范化后的原始文件名（剥离目录前缀、拦截控制字符），未提供时为空 |
| metadata | JSONB | 可空 | 原件探测扩展元数据（JSONB），Java 侧通过 `PostgresJsonbTypeHandler` 映射为 `Map<String, Object>`；图片归档就绪时写入文件头编码宽高 `width`、`height`（未做 EXIF 方向校正，展示布局以派生规格元数据为准） |
| owner_type | VARCHAR(64) | 可空（与 `owner_id` 成对非空或同空） | 持有该资产的业务记录类型标识（如专辑封面、用户头像等）；资产最多由一条业务记录持有 |
| owner_id | BIGINT | 可空（与 `owner_type` 成对非空或同空） | 持有该资产的业务记录主键 ID |
| confirm_expires_at | TIMESTAMPTZ | NOT NULL | 上传确认截止时间（UTC 带时区时间戳），签发凭证过期时间加 5 分钟 |
| confirmed_at | TIMESTAMPTZ | 可空 | 首次确认成功时间（UTC 带时区时间戳），为空表示尚未确认 |
| bound_at | TIMESTAMPTZ | 可空 | 首次绑定业务记录的时间（UTC 带时区时间戳） |
| unbound_expires_at | TIMESTAMPTZ | 可空 | 未绑定原件的过期清理截止时间（进入 `READY` 时写入就绪时间加 24 小时，绑定成功或进入 `DELETING` 时清空） |
| failure_code | VARCHAR(64) | 可空 | 原件校验或处理终态失败（`REJECTED`/`FAILED`）时的原因码 |
| failure_message | TEXT | 可空 | 原件校验或处理终态失败的补充诊断详情 |
| purged_at | TIMESTAMPTZ | 可空 | 原件及关联衍生物在对象存储中全部物理删除完成的时间（UTC 带时区时间戳） |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 记录创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 记录最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_file_assets | 主键唯一 | id | 按资产主键加行锁执行确认、绑定、替换、撤回与后台任务归档推进 |
| uk_file_assets_staging_key | 唯一 | staging_key | 保证每个暂存直传对象键仅对应一条资产建档记录 |
| uk_file_assets_storage_scope_object_key | 部分唯一索引 (`WHERE object_key IS NOT NULL`) | storage_scope, object_key | 保证同一逻辑桶下正式归档原件键与资产记录一对一绑定，未归档记录不占用索引 |
| idx_file_assets_uploader_account_id | 普通 | uploader_account_id | 支撑 `fk_file_assets_user_accounts` 外键删除限制检查（`ON DELETE RESTRICT`）及按上传账号查询 |
| idx_file_assets_owner | 部分索引 (`WHERE owner_type IS NOT NULL`) | owner_type, owner_id | 支撑按业务类型与业务记录 ID 检索关联文件资产 |
| idx_file_assets_unconfirmed_expiry | 部分索引 (`WHERE status = 'PENDING_UPLOAD' AND confirmed_at IS NULL`) | confirm_expires_at, id | 支撑 `FileLifecycleTask.expirePendingUploads` 按截止时间顺序跳过锁批量将超时未确认记录推进为 `EXPIRED`，避免历史终态持续进入扫描 |
| idx_file_assets_unbound_expiry | 部分索引 (`WHERE status = 'READY' AND owner_type IS NULL AND unbound_expires_at IS NOT NULL`) | unbound_expires_at, id | 支撑 `FileLifecycleTask.reclaimExpiredUnboundAssets` 扫描超过 24 小时未绑定的 `READY` 资产并转入 `DELETING` |

**关系**

| 关系 | 目标表 | 外键字段 | 说明 |
| --- | --- | --- | --- |
| N:1 | user_accounts | uploader_account_id | 资产签发上传所属账号，外键约束 `ON DELETE RESTRICT` |

**建表 SQL**

```sql
CREATE TABLE file_assets (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    uploader_account_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    storage_scope VARCHAR(16) NOT NULL,
    staging_key VARCHAR(1024) NOT NULL,
    staging_etag VARCHAR(256),
    object_key VARCHAR(1024),
    content_type VARCHAR(128) NOT NULL,
    byte_size BIGINT NOT NULL,
    etag VARCHAR(256),
    original_filename VARCHAR(255),
    metadata JSONB,
    owner_type VARCHAR(64),
    owner_id BIGINT,
    confirm_expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    bound_at TIMESTAMPTZ,
    unbound_expires_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    failure_message TEXT,
    purged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_file_assets PRIMARY KEY (id),
    CONSTRAINT uk_file_assets_staging_key UNIQUE (staging_key),
    CONSTRAINT fk_file_assets_user_accounts FOREIGN KEY (uploader_account_id) REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_file_assets_byte_size CHECK (byte_size > 0),
    CONSTRAINT ck_file_assets_owner_pair CHECK ((owner_type IS NULL) = (owner_id IS NULL))
);
CREATE UNIQUE INDEX uk_file_assets_storage_scope_object_key ON file_assets (storage_scope, object_key)
WHERE object_key IS NOT NULL;
CREATE INDEX idx_file_assets_uploader_account_id ON file_assets (uploader_account_id);
CREATE INDEX idx_file_assets_owner ON file_assets (owner_type, owner_id)
WHERE owner_type IS NOT NULL;
CREATE INDEX idx_file_assets_unconfirmed_expiry ON file_assets (confirm_expires_at, id)
WHERE status = 'PENDING_UPLOAD' AND confirmed_at IS NULL;
CREATE INDEX idx_file_assets_unbound_expiry ON file_assets (unbound_expires_at, id)
WHERE status = 'READY' AND owner_type IS NULL AND unbound_expires_at IS NOT NULL;
COMMENT ON TABLE file_assets IS '文件资产表';
COMMENT ON COLUMN file_assets.id IS '主键 ID';
COMMENT ON COLUMN file_assets.uploader_account_id IS '上传账号 ID';
COMMENT ON COLUMN file_assets.kind IS '文件类型';
COMMENT ON COLUMN file_assets.status IS '资产状态';
COMMENT ON COLUMN file_assets.storage_scope IS '原件逻辑存储范围';
COMMENT ON COLUMN file_assets.staging_key IS '暂存区对象键';
COMMENT ON COLUMN file_assets.staging_etag IS '暂存对象 ETag';
COMMENT ON COLUMN file_assets.object_key IS '归档原件对象键';
COMMENT ON COLUMN file_assets.content_type IS '内容类型';
COMMENT ON COLUMN file_assets.byte_size IS '字节大小';
COMMENT ON COLUMN file_assets.etag IS '归档原件 ETag';
COMMENT ON COLUMN file_assets.original_filename IS '原始文件名';
COMMENT ON COLUMN file_assets.metadata IS '原件扩展元数据';
COMMENT ON COLUMN file_assets.owner_type IS '归属业务类型';
COMMENT ON COLUMN file_assets.owner_id IS '归属业务记录 ID';
COMMENT ON COLUMN file_assets.confirm_expires_at IS '上传确认截止时间';
COMMENT ON COLUMN file_assets.confirmed_at IS '上传确认时间';
COMMENT ON COLUMN file_assets.bound_at IS '业务绑定时间';
COMMENT ON COLUMN file_assets.unbound_expires_at IS '未绑定原件过期清理时间';
COMMENT ON COLUMN file_assets.failure_code IS '校验或处理失败原因码';
COMMENT ON COLUMN file_assets.failure_message IS '校验或处理失败详情';
COMMENT ON COLUMN file_assets.purged_at IS '对象存储物理删除完成时间';
COMMENT ON COLUMN file_assets.created_at IS '创建时间';
COMMENT ON COLUMN file_assets.updated_at IS '更新时间';
```

## file_variants 表

文件衍生物规格表，保存业务绑定后按需申请的具名规格版本、生成状态、逻辑存储范围、产物对象键与 ETag、JSONB 扩展元数据（含图片校正后宽高）、失败诊断、撤回时间及物理清理时间。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| asset_id | BIGINT | FK, NOT NULL | 关联文件资产 ID，外键引用 `file_assets.id`（`ON DELETE RESTRICT`） |
| code | VARCHAR(32) | NOT NULL | 规格编码名称，当前图片原图级预设为 `FULL` |
| preset_revision | INTEGER | NOT NULL, CHECK (`preset_revision > 0`) | 规格预设修订版本号，当前 `ImageVariantPreset.FULL` 注册为第 `1` 版 |
| status | VARCHAR(32) | NOT NULL | 衍生物状态枚举名称：`PENDING`=待生成，`READY`=已生成可交付，`FAILED`=生成失败可显式重试，`WITHDRAWN`=已撤回停发 |
| storage_scope | VARCHAR(16) | NOT NULL | 逻辑存储范围枚举名称：`PRIVATE`=私有桶，`DELIVERY`=公开交付桶 |
| object_key | VARCHAR(1024) | 可空（非空时与 `storage_scope` 联合唯一） | 衍生物产物在对象存储中的尝试级完整键（`variants/{assetId}/{variantId}/{jobId}/{attempt}/output`），生成进入 `READY` 时写入 |
| content_type | VARCHAR(128) | 可空 | 实际输出编码 MIME 类型（如 `image/jpeg` 或 `image/png`），进入 `READY` 时写入 |
| byte_size | BIGINT | 可空, CHECK (`byte_size IS NULL OR byte_size > 0`) | 衍生物产物字节大小，进入 `READY` 时写入 |
| etag | VARCHAR(256) | 可空 | 衍生物对象 ETag 原值，进入 `READY` 时写入 |
| metadata | JSONB | 可空 | 衍生物扩展元数据（JSONB），Java 侧通过 `PostgresJsonbTypeHandler` 映射为 `Map<String, Object>`；图片衍生物写入 EXIF 校正后的 `width` 与 `height` |
| failure_code | VARCHAR(64) | 可空 | 最近一次生成失败原因码，显式重试或成功时清空 |
| failure_message | TEXT | 可空 | 最近一次生成失败详情，显式重试或成功时清空 |
| withdrawn_at | TIMESTAMPTZ | 可空 | 衍生物被撤回停发的时间（UTC 带时区时间戳） |
| purged_at | TIMESTAMPTZ | 可空 | 衍生物物理对象删除完成时间（UTC 带时区时间戳），完成后状态保持 `WITHDRAWN` |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 记录创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 记录最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_file_variants | 主键唯一 | id | 按衍生物主键查询状态、解析访问地址、重试、撤回与任务落库加锁 |
| uk_file_variants_asset_id_id | 唯一 | asset_id, id | 支撑 `file_jobs(asset_id, variant_id)` 组合外键校验衍生物归属同一资产，同时以前导列 `asset_id` 支撑按资产顺序加锁查询与 `fk_file_variants_file_assets` 检查 |
| uk_file_variants_active_spec | 部分唯一索引 (`WHERE status <> 'WITHDRAWN'`) | asset_id, code, preset_revision, storage_scope | 保证同一资产下同一规格编码、版本与存储范围仅存在一条未撤回记录，实现重复申请幂等，撤回后重新申请可生成新记录 |
| uk_file_variants_storage_scope_object_key | 部分唯一索引 (`WHERE object_key IS NOT NULL`) | storage_scope, object_key | 保证同一逻辑桶下已落盘衍生物对象键唯一，防止重复登记或误覆盖 |

**关系**

| 关系 | 目标表 | 外键字段 | 说明 |
| --- | --- | --- | --- |
| N:1 | file_assets | asset_id | 衍生物归属的文件原件资产，外键约束 `ON DELETE RESTRICT` |

**建表 SQL**

```sql
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
```

## file_jobs 表

文件异步处理任务与 Outbox 调度表，保存原件校验、衍生物生成与对象物理清理任务的队列派发租约、补派计时、执行租约、活跃防重键、失败诊断详情及终态完成时间（结束满 30 天后由定时任务清理历史流水）。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| asset_id | BIGINT | FK, NOT NULL | 关联文件资产 ID，外键引用 `file_assets.id`（`ON DELETE RESTRICT`） |
| variant_id | BIGINT | FK, 可空 | 关联衍生物 ID，`GENERATE_VARIANT` 与 `CLEANUP_VARIANT` 必填，通过 `(asset_id, variant_id)` 组合外键引用 `file_variants (asset_id, id)` 保证归属同一资产 |
| type | VARCHAR(32) | NOT NULL | 任务类型枚举名称：`VALIDATE_UPLOAD`=暂存原件校验归档，`GENERATE_VARIANT`=衍生物生成，`CLEANUP_ASSET`=整份资产物理清理，`CLEANUP_VARIANT`=单个已撤回衍生物物理清理 |
| status | VARCHAR(16) | NOT NULL | 任务状态枚举名称：`PENDING`=待派发/待重试，`RUNNING`=执行中，`SUCCEEDED`=已成功，`FAILED`=终态失败，`CANCELLED`=目标已失效取消 |
| attempt_count | INTEGER | NOT NULL, DEFAULT `0`, CHECK (`attempt_count >= 0`) | 已领取执行次数，Worker 抢占执行时原子递增 |
| available_at | TIMESTAMPTZ | NOT NULL | 最早可执行时间（UTC 带时区时间戳），失败重排时按退避延迟推后 |
| lease_until | TIMESTAMPTZ | 可空 | 本次执行的租期截止时间（UTC 带时区时间戳），仅 `RUNNING` 期间非空，终态或重排时清空 |
| dispatch_after | TIMESTAMPTZ | 可空 | 最早可派发或补派时间（UTC 带时区时间戳）；创建/重排时等于 `available_at`，领取发送时置为 `+15s`，Broker 确认未退回后按类型写入 `+30s`/`+60s`/`+5min`，进入 `RUNNING` 或终态时清空 |
| dispatch_token | VARCHAR(36) | 可空 | 当前派发批次的 UUID 字符串令牌，Broker 确认回写、进入执行或终态时清空 |
| dispatched_at | TIMESTAMPTZ | 可空 | 本轮 Broker 确认或消费领取时间（UTC 带时区时间戳），失败重排回 `PENDING` 时清空 |
| dedup_key | VARCHAR(128) | 可空（活跃态部分唯一） | 活跃任务防重键（如 `{assetId}:VALIDATE_UPLOAD`、`{assetId}:GENERATE_VARIANT:{variantId}`、`{assetId}:CLEANUP_ASSET`、`{assetId}:CLEANUP_VARIANT:{variantId}`） |
| payload | JSONB | 可空 | 任务扩展参数（JSONB），Java 侧通过 `PostgresJsonbTypeHandler` 映射为 `Map<String, Object>` |
| failure_code | VARCHAR(64) | 可空 | 最近一次执行失败原因码（如 `SOURCE_MISSING`、`SOURCE_CHANGED`、`IMAGE_TOO_LARGE`、`INVALID_IMAGE`、`UNSUPPORTED_PRESET`、`STORAGE_ERROR`、`LEASE_EXPIRED`），成功时清空 |
| failure_message | TEXT | 可空 | 最近一次执行失败补充详情（应用层截断至 1000 字符），成功时清空 |
| finished_at | TIMESTAMPTZ | 可空 | 任务进入终态（`SUCCEEDED`、`FAILED` 或 `CANCELLED`）的完成时间（UTC 带时区时间戳），用于 30 天历史流水回收 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 记录创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 记录最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_file_jobs | 主键唯一 | id | 消费端按任务主键原子抢占、状态推进及批量确认回写定位 |
| idx_file_jobs_asset_id | 普通 | asset_id | 支撑 `fk_file_jobs_file_assets` 外键检查及删除资产时批量取消该资产下待执行任务 |
| idx_file_jobs_asset_variant | 部分索引 (`WHERE variant_id IS NOT NULL`) | asset_id, variant_id | 支撑 `fk_file_jobs_file_variants` 组合外键检查及撤回衍生物时取消待执行生成任务 |
| idx_file_jobs_dispatch_after | 部分索引 (`WHERE status = 'PENDING' AND dispatch_after IS NOT NULL`) | dispatch_after, id | 支撑 `FileJobDispatcher` 按队列分组跳过锁批量领取待派发或需补派的任务，进入执行或终态后自动退出索引 |
| idx_file_jobs_lease_until | 部分索引 (`WHERE status = 'RUNNING' AND lease_until IS NOT NULL`) | lease_until, id | 支撑 `FileJobReaper` 按租期截止时间顺序扫描并回收超时的 `RUNNING` 任务 |
| uk_file_jobs_active_dedup_key | 部分唯一索引 (`WHERE dedup_key IS NOT NULL AND status IN ('PENDING', 'RUNNING')`) | dedup_key | 防止同一防重键在活跃态并发插入重复任务，配合 `ON CONFLICT ... DO NOTHING` 实现幂等入队，任务进入终态后释放防重锁 |
| idx_file_jobs_finished_at | 部分索引 (`WHERE finished_at IS NOT NULL`) | finished_at, id | 支撑 `FileLifecycleTask.purgeFinishedJobs` 按完成时间分批清理结束满 30 天的历史任务流水 |

**关系**

| 关系 | 目标表 | 外键字段 | 说明 |
| --- | --- | --- | --- |
| N:1 | file_assets | asset_id | 任务归属的文件原件资产，外键约束 `ON DELETE RESTRICT` |
| N:1 | file_variants | asset_id, variant_id | 衍生物生成或清理任务关联的目标衍生物，组合外键约束 `ON DELETE RESTRICT` 保证与 `asset_id` 一致 |

**建表 SQL**

```sql
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
```
