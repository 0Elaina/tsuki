# 数据库与建表通用约定

## 迁移脚本组织

- Flyway 迁移脚本统一存放在 `backend/tsuki-app/src/main/resources/db/migration/`，遵循 `V<版本>__<描述>.sql` 命名。
- 结构变更仅通过新增递增版本迁移脚本推进，不直接修改已应用到环境中的历史脚本。

## 命名与主键约定

- 表名、字段名使用小写蛇形命名（`snake_case`）。
- 业务表主键统一使用 `BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL`；对外 API DTO 中的业务 ID 序列化为字符串传递。
- 命名约束统一使用以下前缀：
  - 主键：`pk_<表名>`
  - 唯一约束：`uk_<表名>_<列名>`
  - 外键：`fk_<表名>_<目标表>`
  - 检查约束：`ck_<表名>_<规则>`

## 类型、时间与枚举约定

- 固定上限的短文本使用 `VARCHAR(n)`，密码哈希及长文本使用 `TEXT`。
- 创建和更新时间字段统一使用 `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`，Java 实体映射为 `OffsetDateTime`，配合公共模块 `MybatisPlusConfig` 的 `MetaObjectHandler` 或数据库默认值填充。
- 简单、稳定的状态和角色字段按枚举名称字符串（`VARCHAR`）存储，值域由 Java 枚举维护。
- 所有表和字段在迁移脚本中通过 `COMMENT ON` 补充中文说明，枚举字段需写明取值映射，并与数据库设计文档保持一致。

