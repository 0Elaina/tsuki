# 分类与标签模块 API

## POST /api/v1/admin/categories

校验管理端 Bearer Access JWT（`ROLE_ADMIN`），创建新的曲库分区（初始状态为 `ACTIVE`）并返回创建结果（HTTP `201 Created`）。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Authorization | string | header | ✅ | Bearer 认证头，格式为 `Bearer <accessToken>` |
| name | string | body | ✅ | 分区名称，去首尾空白后非空白，长度 1~64 个字符，大小写不敏感唯一 |
| sortOrder | int | body | ❌ | 排序值，非负整数（`0`~`2147483647`），省略或为 `null` 时默认为 `0` |

**响应示例**

```json
{
  "data": {
    "id": "1",
    "name": "Rock",
    "sortOrder": 0,
    "status": "ACTIVE",
    "createdAt": "2026-11-16T07:00:00.123456789Z",
    "updatedAt": "2026-11-16T07:00:00.123456789Z"
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

`status` 枚举取值：`ACTIVE`（启用）、`DISABLED`（停用）。

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 400 | BAD_REQUEST |
| 400 | VALIDATION_FAILED |
| 401 | UNAUTHENTICATED |
| 403 | ACCESS_DENIED |
| 409 | CATEGORY_NAME_CONFLICT |

## GET /api/v1/admin/categories/{id}

校验管理端 Bearer Access JWT（`ROLE_ADMIN`），按主键 ID 查询分区详情（启用与停用状态均可查看）。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Authorization | string | header | ✅ | Bearer 认证头，格式为 `Bearer <accessToken>` |
| id | long | path | ✅ | 分区主键 ID，必须为正整数（大于 0） |

**响应示例**

```json
{
  "data": {
    "id": "1",
    "name": "Rock",
    "sortOrder": 0,
    "status": "ACTIVE",
    "createdAt": "2026-11-16T07:00:00.123456Z",
    "updatedAt": "2026-11-16T07:00:00.123456Z"
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

`status` 枚举取值：`ACTIVE`（启用）、`DISABLED`（停用）。

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 400 | VALIDATION_FAILED |
| 401 | UNAUTHENTICATED |
| 403 | ACCESS_DENIED |
| 404 | CATEGORY_NOT_FOUND |

## GET /api/v1/admin/categories

分页查询曲库分区，按 `sortOrder` 升序排列，同值按 `id` 升序排列。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Authorization | string | header | ✅ | 管理员 Bearer Access JWT，格式为 `Bearer <accessToken>` |
| page | int | query | ❌ | 页码，默认 `1`，最小 `1` |
| pageSize | int | query | ❌ | 页大小，默认 `20`，范围 `1`~`100` |
| keyword | string | query | ❌ | 分区名称关键词，去首尾空白后做大小写不敏感的包含匹配，空白时查全部名称 |
| status | string | query | ❌ | 分区状态，`ACTIVE` 为启用，`DISABLED` 为停用。省略时查询所有状态 |

**响应示例**

```json
{
  "data": {
    "items": [
      {
        "id": "1",
        "name": "Indie Rock",
        "sortOrder": 0,
        "status": "ACTIVE",
        "createdAt": "2026-11-16T07:00:00.123456Z",
        "updatedAt": "2026-11-16T07:00:00.123456Z"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

`total` 是满足全部筛选条件的分区数。指定页无数据时，`items` 返回空数组。

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 400 | VALIDATION_FAILED |
| 401 | UNAUTHENTICATED |
| 403 | ACCESS_DENIED |

## PATCH /api/v1/admin/categories/{id}

校验管理端 Bearer Access JWT（`ROLE_ADMIN`），编辑指定分区的名称与排序值（不改变原有状态），返回更新后的分区信息。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Authorization | string | header | ✅ | Bearer 认证头，格式为 `Bearer <accessToken>` |
| id | long | path | ✅ | 分区主键 ID，必须为正整数（大于 0） |
| name | string | body | ✅ | 分区名称，去首尾空白后非空白，长度 1~64 个字符，大小写不敏感唯一 |
| sortOrder | int | body | ✅ | 排序值，必填且不能为 `null`，非负整数（`0`~`2147483647`） |

**响应示例**

```json
{
  "data": {
    "id": "1",
    "name": "Post-Rock",
    "sortOrder": 10,
    "status": "ACTIVE",
    "createdAt": "2026-11-16T07:00:00.123456Z",
    "updatedAt": "2026-11-16T15:10:00.123456Z"
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

`status` 枚举取值：`ACTIVE`（启用）、`DISABLED`（停用）。

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 400 | BAD_REQUEST |
| 400 | VALIDATION_FAILED |
| 401 | UNAUTHENTICATED |
| 403 | ACCESS_DENIED |
| 404 | CATEGORY_NOT_FOUND |
| 409 | CATEGORY_NAME_CONFLICT |

## PUT /api/v1/admin/categories/{id}/status

校验管理端 Bearer Access JWT（`ROLE_ADMIN`），更新指定分区的启用或停用状态（重复提交相同状态仍返回成功），返回更新后的分区信息。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Authorization | string | header | ✅ | Bearer 认证头，格式为 `Bearer <accessToken>` |
| id | long | path | ✅ | 分区主键 ID，必须为正整数（大于 0） |
| status | string | body | ✅ | 目标状态，取值：`ACTIVE`=启用，`DISABLED`=停用 |

**响应示例**

```json
{
  "data": {
    "id": "1",
    "name": "Post-Rock",
    "sortOrder": 10,
    "status": "DISABLED",
    "createdAt": "2026-11-16T07:00:00.123456Z",
    "updatedAt": "2026-11-16T15:12:00.123456Z"
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

`status` 枚举取值：`ACTIVE`（启用）、`DISABLED`（停用）。

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 400 | BAD_REQUEST |
| 400 | VALIDATION_FAILED |
| 401 | UNAUTHENTICATED |
| 403 | ACCESS_DENIED |
| 404 | CATEGORY_NOT_FOUND |

## DELETE /api/v1/admin/categories/{id}

校验管理端 Bearer Access JWT（`ROLE_ADMIN`），按主键 ID 幂等删除指定分区（启用与停用状态均可删除，目标不存在时同样返回成功）。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Authorization | string | header | ✅ | Bearer 认证头，格式为 `Bearer <accessToken>` |
| id | long | path | ✅ | 分区主键 ID，必须为正整数（大于 0） |

**响应示例**

```json
{
  "data": null,
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 400 | VALIDATION_FAILED |
| 401 | UNAUTHENTICATED |
| 403 | ACCESS_DENIED |

## POST /api/v1/admin/categories/batch-status

校验管理端 Bearer Access JWT（`ROLE_ADMIN`），校验全部目标存在后批量切换分区状态（跳过已处于目标状态的记录），返回实际变更行数。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Authorization | string | header | ✅ | Bearer 认证头，格式为 `Bearer <accessToken>` |
| ids | long[] | body | ✅ | 分区 ID 数组，去重前长度 `1`~`100`，元素均为正整数（兼容数字或十进制数字字符串） |
| status | string | body | ✅ | 目标状态，取值：`ACTIVE`=启用，`DISABLED`=停用 |

**响应示例**

```json
{
  "data": 2,
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

`data` 为本次实际切换状态的行数；已处于目标状态的记录保留原 `updatedAt` 且不计入行数。

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 400 | BAD_REQUEST |
| 400 | VALIDATION_FAILED |
| 401 | UNAUTHENTICATED |
| 403 | ACCESS_DENIED |
| 404 | CATEGORY_NOT_FOUND |

## POST /api/v1/admin/categories/batch-delete

校验管理端 Bearer Access JWT（`ROLE_ADMIN`），按主键集合幂等批量删除分区（启用与停用状态均可删除，缺失 ID 不报错），返回实际删除行数。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Authorization | string | header | ✅ | Bearer 认证头，格式为 `Bearer <accessToken>` |
| ids | long[] | body | ✅ | 分区 ID 数组，去重前长度 `1`~`100`，元素均为正整数（兼容数字或十进制数字字符串） |

**响应示例**

```json
{
  "data": 2,
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

`data` 为本次实际删除的行数；已不存在的 ID 不计入行数，全部已删除时返回 `0`。

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 400 | BAD_REQUEST |
| 400 | VALIDATION_FAILED |
| 401 | UNAUTHENTICATED |
| 403 | ACCESS_DENIED |
