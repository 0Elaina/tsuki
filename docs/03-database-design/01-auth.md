# 认证数据库设计

数据库与命名约定见 [数据库通用约定](00-conventions.md)。

## admins 表

保存系统管理员账号、密码哈希与认证版本信息，对应实体 `tsuki.modules.auth.models.Admin`。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明（枚举须写全映射） |
| --- | --- | --- | --- |
| id | BIGINT | PK, NOT NULL, GENERATED ALWAYS AS IDENTITY | 管理员主键标识 |
| username | VARCHAR(32) | UNIQUE, NOT NULL | 管理员唯一登录名，区分大小写 |
| password_hash | TEXT | NOT NULL | Argon2 编码后的密码哈希 |
| auth_version | INTEGER | NOT NULL, DEFAULT 1 | 认证版本号，重置密码时递增使旧 JWT 失效 |
| created_at | TIMESTAMPTZ | NOT NULL | ORM 插入时调用 `utc_now()` 填充 UTC 创建时间 |
| updated_at | TIMESTAMPTZ | NOT NULL | ORM 插入和更新时调用 `utc_now()` 填充 UTC 时间，重置密码时更新 |

两个时间字段使用 SQLAlchemy 的客户端 `default`、`onupdate`，数据库列本身没有 DEFAULT 或更新时间触发器。`auth_version` 是递增计数器，不是枚举；重置密码时与密码哈希在同一事务内更新。

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_admins | 主键 | id | 认证拦截时按管理员 ID 查询实体并核对版本号 |
| uk_admins_username | 唯一 | username | 登录认证时按用户名精准匹配账号，并防止同名重复创建 |

**关系**

无外键关系。

**建表 SQL**

```sql
CREATE TABLE admins (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    username VARCHAR(32) NOT NULL,
    password_hash TEXT NOT NULL,
    auth_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_admins PRIMARY KEY (id),
    CONSTRAINT uk_admins_username UNIQUE (username)
);
```

迁移定义见 [46421ab28a85_create_admins_table.py](../../backend/migrations/versions/46421ab28a85_create_admins_table.py)，ORM 实体见 [models.py](../../backend/src/tsuki/modules/auth/models.py)。
