# 用户模块 API

## GET /api/v1/admin/me

校验管理端 Bearer Access JWT（`ROLE_ADMIN`），查询并返回当前活跃管理员账号的基础资料。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Authorization | string | header | ✅ | Bearer 认证头，格式为 `Bearer <accessToken>` |

**响应示例**

```json
{
  "data": {
    "id": "1",
    "username": "admin",
    "nickname": "admin",
    "role": "ADMIN"
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

`role` 枚举取值：`ADMIN`（管理员）、`USER`（普通用户）。

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 401 | UNAUTHENTICATED |
| 403 | ACCESS_DENIED |
| 403 | ACCOUNT_DISABLED |
| 404 | ACCOUNT_NOT_FOUND |

