# 账号模块数据库设计

## user_accounts 表

统一账号表，保存管理端与听众端账号凭据、角色与状态。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明（枚举须写全映射） |
| --- | --- | --- | --- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY, NOT NULL | 主键 ID |
| username | VARCHAR(32) | UNIQUE, NOT NULL, CHECK(`ck_user_accounts_username_format`) | 规范化小写登录用户名，在 `C` 排序规则下匹配 `^[a-z][a-z0-9_]{2,31}$` |
| nickname | VARCHAR(32) | NOT NULL | 展示昵称，允许重复 |
| password_hash | TEXT | NOT NULL | `DelegatingPasswordEncoder` 格式的密码哈希（`{bcrypt}...`） |
| role | VARCHAR(16) | NOT NULL | 账号角色枚举名称：`ADMIN`=管理员，`USER`=听众用户 |
| status | VARCHAR(16) | NOT NULL | 账号状态枚举名称：`ACTIVE`=正常启用，`DISABLED`=停用 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 创建时间（UTC 带时区时间戳） |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 最后更新时间（UTC 带时区时间戳） |

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_user_accounts | 主键唯一 | id | `AccountService.findById` 按主键查询账号身份，以及后续业务表外键引用 |
| uk_user_accounts_username | 唯一 | username | `AccountAuthenticateService.authenticate` 按用户名精确查询登录凭据，并防止重复创建账号 |

**关系**

当前暂无外键关系。后续听众资料与认证会话表将关联本表 `id`。

**建表 SQL**

```sql
CREATE TABLE user_accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    username VARCHAR(32) NOT NULL,
    nickname VARCHAR(32) NOT NULL,
    password_hash TEXT NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_accounts PRIMARY KEY (id),
    CONSTRAINT uk_user_accounts_username UNIQUE (username),
    CONSTRAINT ck_user_accounts_username_format CHECK (username COLLATE "C" ~ '^[a-z][a-z0-9_]{2,31}$')
);

COMMENT ON TABLE user_accounts IS '统一账号表，保存管理端与听众端账号凭据、角色与状态';
COMMENT ON COLUMN user_accounts.id IS '主键 ID';
COMMENT ON COLUMN user_accounts.username IS '规范化小写登录用户名，匹配 ^[a-z][a-z0-9_]{2,31}$';
COMMENT ON COLUMN user_accounts.nickname IS '展示昵称，允许重复';
COMMENT ON COLUMN user_accounts.password_hash IS 'DelegatingPasswordEncoder 格式的密码哈希（{bcrypt}...）';
COMMENT ON COLUMN user_accounts.role IS '账号角色枚举名称：ADMIN=管理员，USER=听众用户';
COMMENT ON COLUMN user_accounts.status IS '账号状态枚举名称：ACTIVE=正常启用，DISABLED=停用';
COMMENT ON COLUMN user_accounts.created_at IS '创建时间（UTC 带时区时间戳）';
COMMENT ON COLUMN user_accounts.updated_at IS '最后更新时间（UTC 带时区时间戳）';
```

