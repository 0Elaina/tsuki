# 分类与标签模块 API

## POST /api/v1/admin/categories

校验管理端 Bearer Access JWT（`ROLE_ADMIN`），创建新的曲库分区（初始状态为 `ACTIVE`）并返回创建结果（HTTP `201 Created`）。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Authorization | string | header | ✅ | Bearer 认证头，格式为 `Bearer <accessToken>` |
| name | string | body | ✅ | 分区名称，经 NFKC 规范化与去首尾空白后非空白，长度 1~64 个字符，大小写不敏感唯一 |
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
