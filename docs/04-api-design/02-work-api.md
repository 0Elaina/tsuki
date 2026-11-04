# 作品管理 API

公共响应字段、校验错误结构和系统错误见 [统一响应与全局错误码](00-error-codes.md)。

## GET /api/v1/admin/works

按关键词、媒介类型和发行状态筛选，分页查看作品列表。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明（枚举须写全映射） |
| --- | --- | --- | --- | --- |
| Cookie | string | header | ✅ | 包含有效 `tsuki_admin_token`，由浏览器自动携带 |
| keyword | string | query | ❌ | 搜索关键词，去除首尾空白后最长 255 字符，匹配展示名、原文名或别名 |
| media_type | string | query | ❌ | 媒介类型：anime=动画, manga=漫画, novel=小说, game=游戏 |
| release_status | string | query | ❌ | 发行状态：unknown=未知, not_released=未发行, releasing=发行中, finished=已发行/完结, hiatus=暂停, canceled=取消 |
| page | int | query | ❌ | 请求页码，必须大于等于 1，默认 1 |
| page_size | int | query | ❌ | 每页条数，范围 1～100，默认 20 |

**响应数据**

成功状态为 HTTP 200，`data` 为分页对象，包含以下字段。

| 字段 | JSON 类型 | 说明 |
| --- | --- | --- |
| data.items | array | 当前页作品列表 |
| data.items[].id | integer | 作品 ID |
| data.items[].name | string | 展示名 |
| data.items[].original_name | string 或 null | 原文名 |
| data.items[].media_type | string | 媒介类型（枚举值） |
| data.items[].release_status | string | 发行状态（枚举值） |
| data.items[].updated_at | string | UTC 更新时间，ISO 8601 格式 |
| data.total | integer | 满足筛选条件的作品总数 |
| data.page | integer | 请求页码 |
| data.page_size | integer | 请求的每页条数 |

**响应示例**

```json
{
  "code": "SUCCESS",
  "message": "响应成功",
  "data": {
    "items": [
      {
        "id": 1,
        "name": "命运石之门",
        "original_name": "Steins;Gate",
        "media_type": "anime",
        "release_status": "finished",
        "updated_at": "2026-11-02T15:00:00Z"
      }
    ],
    "total": 1,
    "page": 1,
    "page_size": 20
  }
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 401 | AUTHENTICATION_REQUIRED |
| 401 | INVALID_TOKEN |
| 422 | INVALID_REQUEST |

## GET /api/v1/admin/works/{work_id}

查看指定作品的完整资料与发布日期。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明（枚举须写全映射） |
| --- | --- | --- | --- | --- |
| Cookie | string | header | ✅ | 包含有效 `tsuki_admin_token`，由浏览器自动携带 |
| work_id | int | path | ✅ | 作品 ID，范围 1～9223372036854775807 |

**响应数据**

成功状态为 HTTP 200，`data` 为作品详情对象，包含以下字段。

| 字段 | JSON 类型 | 说明 |
| --- | --- | --- |
| data.id | integer | 作品 ID |
| data.name | string | 展示名 |
| data.original_name | string 或 null | 原文名 |
| data.aliases | array of string | 别名列表，无别名时为空数组 |
| data.summary | string 或 null | 作品简介 |
| data.media_type | string | 媒介类型（枚举值） |
| data.release_status | string | 发行状态（枚举值） |
| data.release_date | object 或 null | 发布日期分量；日期全未知时为 null |
| data.release_date.year | integer | 发行年份 |
| data.release_date.month | integer 或 null | 发行月份，未知时为 null |
| data.release_date.day | integer 或 null | 发行日，未知时为 null |
| data.created_at | string | UTC 创建时间，ISO 8601 格式 |
| data.updated_at | string | UTC 更新时间，ISO 8601 格式 |

**响应示例**

```json
{
  "code": "SUCCESS",
  "message": "响应成功",
  "data": {
    "id": 1,
    "name": "命运石之门",
    "original_name": "Steins;Gate",
    "aliases": ["石头门", "S;G"],
    "summary": "涉谷与秋叶原的时光跳跃故事。",
    "media_type": "anime",
    "release_status": "finished",
    "release_date": {
      "year": 2011,
      "month": 4,
      "day": 6
    },
    "created_at": "2026-11-02T15:00:00Z",
    "updated_at": "2026-11-02T15:00:00Z"
  }
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 401 | AUTHENTICATION_REQUIRED |
| 401 | INVALID_TOKEN |
| 404 | WORK_NOT_FOUND |
| 422 | INVALID_REQUEST |

## POST /api/v1/admin/works

新增作品条目，成功返回创建后的作品完整资料。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明（枚举须写全映射） |
| --- | --- | --- | --- | --- |
| Cookie | string | header | ✅ | 包含有效 `tsuki_admin_token`，由浏览器自动携带 |
| X-CSRF-Protection | string | header | ✅ | 固定值 `1`，非安全方法防护头 |
| name | string | body | ✅ | 作品展示名，去除首尾空白后 1～255 字符 |
| original_name | string 或 null | body | ❌ | 原文名，去除首尾空白，最长 255 字符；默认 null |
| aliases | array of string | body | ❌ | 别名列表，最多 50 项，每项 1～255 字符；默认空数组 |
| summary | string 或 null | body | ❌ | 作品简介，去除首尾空白，最长 10000 字符；默认 null |
| media_type | string | body | ✅ | 媒介类型：anime=动画, manga=漫画, novel=小说, game=游戏 |
| release_status | string | body | ❌ | 发行状态：unknown=未知, not_released=未发行, releasing=发行中, finished=已发行/完结, hiatus=暂停, canceled=取消；默认 unknown |
| release_date | object 或 null | body | ❌ | 发行日期对象，提供时 year 必填；默认 null |
| release_date.year | int | body | ❌ | 发行年份，严格整数 1～9999 |
| release_date.month | int 或 null | body | ❌ | 发行月份，严格整数 1～12；默认 null |
| release_date.day | int 或 null | body | ❌ | 发行日，严格整数 1～31；填写日时必须填写月；默认 null |

**响应数据**

成功状态为 HTTP 200，`data` 为新创建的作品完整详情，字段结构与 `GET /api/v1/admin/works/{work_id}` 完全一致。

**响应示例**

```json
{
  "code": "SUCCESS",
  "message": "响应成功",
  "data": {
    "id": 1,
    "name": "命运石之门",
    "original_name": "Steins;Gate",
    "aliases": ["石头门", "S;G"],
    "summary": "涉谷与秋叶原的时光跳跃故事。",
    "media_type": "anime",
    "release_status": "finished",
    "release_date": {
      "year": 2011,
      "month": 4,
      "day": 6
    },
    "created_at": "2026-11-04T10:00:00Z",
    "updated_at": "2026-11-04T10:00:00Z"
  }
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 401 | AUTHENTICATION_REQUIRED |
| 401 | INVALID_TOKEN |
| 403 | INVALID_CSRF_REQUEST |
| 422 | INVALID_REQUEST |

## PUT /api/v1/admin/works/{work_id}

完整修改指定作品的全部资料，采用替换语义，可写字段全部必传。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明（枚举须写全映射） |
| --- | --- | --- | --- | --- |
| Cookie | string | header | ✅ | 包含有效 `tsuki_admin_token`，由浏览器自动携带 |
| X-CSRF-Protection | string | header | ✅ | 固定值 `1`，非安全方法防护头 |
| work_id | int | path | ✅ | 目标作品 ID，范围 1～9223372036854775807 |
| name | string | body | ✅ | 作品展示名，去除首尾空白后 1～255 字符 |
| original_name | string 或 null | body | ✅ | 原文名，最长 255 字符；清空传 null |
| aliases | array of string | body | ✅ | 别名列表，最多 50 项，每项 1～255 字符；清空传空数组 `[]` |
| summary | string 或 null | body | ✅ | 作品简介，最长 10000 字符；清空传 null |
| media_type | string | body | ✅ | 媒介类型：anime=动画, manga=漫画, novel=小说, game=游戏 |
| release_status | string | body | ✅ | 发行状态：unknown=未知, not_released=未发行, releasing=发行中, finished=已发行/完结, hiatus=暂停, canceled=取消 |
| release_date | object 或 null | body | ✅ | 发行日期对象；日期完全未知清空传 null |
| release_date.year | int | body | ❌ | 发行年份，严格整数 1～9999（提供日期对象时必填） |
| release_date.month | int 或 null | body | ❌ | 发行月份，严格整数 1～12；未知填 null |
| release_date.day | int 或 null | body | ❌ | 发行日，严格整数 1～31；填写日时必须填写月；未知填 null |

**响应数据**

成功状态为 HTTP 200，`data` 为更新后的作品完整详情，`updated_at` 自动刷新为最新时间。

**响应示例**

```json
{
  "code": "SUCCESS",
  "message": "响应成功",
  "data": {
    "id": 1,
    "name": "命运石之门 0",
    "original_name": "Steins;Gate 0",
    "aliases": ["石头门0", "S;G0"],
    "summary": "未能拯救她的世界线故事。",
    "media_type": "anime",
    "release_status": "finished",
    "release_date": {
      "year": 2018,
      "month": 4,
      "day": 12
    },
    "created_at": "2026-11-04T10:00:00Z",
    "updated_at": "2026-11-04T11:00:00Z"
  }
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 401 | AUTHENTICATION_REQUIRED |
| 401 | INVALID_TOKEN |
| 403 | INVALID_CSRF_REQUEST |
| 404 | WORK_NOT_FOUND |
| 422 | INVALID_REQUEST |

## DELETE /api/v1/admin/works/{work_id}

按作品 ID 永久删除指定作品。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明（枚举须写全映射） |
| --- | --- | --- | --- | --- |
| Cookie | string | header | ✅ | 包含有效 `tsuki_admin_token`，由浏览器自动携带 |
| X-CSRF-Protection | string | header | ✅ | 固定值 `1`，非安全方法防护头 |
| work_id | int | path | ✅ | 目标作品 ID，范围 1～9223372036854775807 |

**响应数据**

成功状态为 HTTP 200，`data` 固定为 null。

**响应示例**

```json
{
  "code": "SUCCESS",
  "message": "响应成功",
  "data": null
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 401 | AUTHENTICATION_REQUIRED |
| 401 | INVALID_TOKEN |
| 403 | INVALID_CSRF_REQUEST |
| 404 | WORK_NOT_FOUND |

## POST /api/v1/admin/works/batch-delete

根据作品 ID 列表批量永久删除作品，任一目标不存在则整批回滚，成功返回删除作品数量。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明（枚举须写全映射） |
| --- | --- | --- | --- | --- |
| Cookie | string | header | ✅ | 包含有效 `tsuki_admin_token`，由浏览器自动携带 |
| X-CSRF-Protection | string | header | ✅ | 固定值 `1`，非安全方法防护头 |
| work_ids | array of integer | body | ✅ | 作品 ID 列表，1～100 项；按首次出现顺序去重 |

**响应数据**

成功状态为 HTTP 200，`data` 为实际删除的作品数量整数。

**响应示例**

```json
{
  "code": "SUCCESS",
  "message": "响应成功",
  "data": 2
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 401 | AUTHENTICATION_REQUIRED |
| 401 | INVALID_TOKEN |
| 403 | INVALID_CSRF_REQUEST |
| 404 | WORK_NOT_FOUND |
| 422 | INVALID_REQUEST |



