# 作品数据库设计

数据库与命名约定见 [数据库通用约定](00-conventions.md)。

## works 表

保存作品基础信息、别名、媒介类型、发行状态与发行日期，对应实体 `tsuki.modules.work.models.Work`。

**字段**

| 字段名 | 类型 | 约束(PK/FK/UNIQUE/NOT NULL/DEFAULT) | 说明（枚举须写全映射） |
| --- | --- | --- | --- |
| id | BIGINT | PK, NOT NULL, GENERATED ALWAYS AS IDENTITY | 作品主键标识 |
| name | VARCHAR(255) | NOT NULL, CHECK(char_length > 0) | 作品展示名称，不可为空白字符串 |
| original_name | VARCHAR(255) | NULL | 作品原作/原语言名称 |
| aliases | TEXT[] | NOT NULL, DEFAULT '{}'::text[] | 作品别名数组，用于别名搜索，默认空数组 |
| summary | TEXT | NULL | 作品简介概括 |
| media_type | VARCHAR(32) | NOT NULL, CHECK(IN ('anime', 'manga', 'novel', 'game')) | 媒介类型：anime=动画, manga=漫画, novel=小说, game=游戏 |
| release_status | VARCHAR(32) | NOT NULL, DEFAULT 'unknown', CHECK(IN ('unknown', 'not_released', 'releasing', 'finished', 'hiatus', 'canceled')) | 发行状态：unknown=未知, not_released=未发行, releasing=发行中, finished=已发行/完结, hiatus=暂停, canceled=取消 |
| release_year | SMALLINT | NULL, CHECK(IS NULL OR 1..9999) | 发行年份 |
| release_month | SMALLINT | NULL, CHECK(IS NULL OR 1..12) | 发行月份 |
| release_day | SMALLINT | NULL, CHECK(IS NULL OR 1..31) | 发行日 |
| created_at | TIMESTAMPTZ | NOT NULL | ORM 插入时调用 `utc_now()` 填充 UTC 创建时间 |
| updated_at | TIMESTAMPTZ | NOT NULL | ORM 插入和更新时调用 `utc_now()` 填充 UTC 更新时间 |

两个时间字段沿用项目惯例，由 ORM 在写入和更新时调用 `utc_now()`，列本身没有数据库 DEFAULT 或更新触发器。日期分量通过 `ck_works_release_date_shape` 确保形状完整（支持全空、仅年、年月、年月日，拒绝无年有月或无月有日）；日历合法性校验由业务写入层负责。

**索引**

| 索引名 | 类型 | 字段 | 理由（必须说明具体查询场景） |
| --- | --- | --- | --- |
| pk_works | 主键 | id | 按 ID 获取作品详情及列表按主键倒序扫描 |

**关系**

无外键关系。

**建表 SQL**

```sql
CREATE TABLE works (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    aliases TEXT[] DEFAULT '{}'::text[] NOT NULL,
    summary TEXT,
    media_type VARCHAR(32) NOT NULL,
    release_status VARCHAR(32) DEFAULT 'unknown' NOT NULL,
    release_year SMALLINT,
    release_month SMALLINT,
    release_day SMALLINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_works PRIMARY KEY (id),
    CONSTRAINT ck_works_name_not_empty CHECK (char_length(name) > 0),
    CONSTRAINT ck_works_media_type CHECK (media_type IN ('anime', 'manga', 'novel', 'game')),
    CONSTRAINT ck_works_release_status CHECK (release_status IN ('unknown', 'not_released', 'releasing', 'finished', 'hiatus', 'canceled')),
    CONSTRAINT ck_works_release_year_range CHECK (release_year IS NULL OR (release_year >= 1 AND release_year <= 9999)),
    CONSTRAINT ck_works_release_month_range CHECK (release_month IS NULL OR (release_month >= 1 AND release_month <= 12)),
    CONSTRAINT ck_works_release_day_range CHECK (release_day IS NULL OR (release_day >= 1 AND release_day <= 31)),
    CONSTRAINT ck_works_release_date_shape CHECK (
        (release_year IS NULL AND release_month IS NULL AND release_day IS NULL)
        OR (release_year IS NOT NULL AND (release_day IS NULL OR release_month IS NOT NULL))
    )
);
```

迁移定义见 [e8b21fc6fbad_create_works_table.py](../../backend/migrations/versions/e8b21fc6fbad_create_works_table.py)，ORM 实体见 [models.py](../../backend/src/tsuki/modules/work/models.py)。

