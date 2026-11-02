# 管理员认证 API

公共响应字段、校验错误结构和系统错误见 [统一响应与全局错误码](00-error-codes.md)。

## POST /api/v1/admin/auth/login

管理员密码登录，成功后签发固定有效期的 JWT 并写入 HttpOnly Cookie，不在响应正文返回令牌。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明（枚举须写全映射） |
| --- | --- | --- | --- | --- |
| X-CSRF-Protection | string | header | ✅ | 固定值 `1`，用于防御跨站请求伪造 |
| username | string | body | ✅ | 去除首尾空白后校验，区分大小写；3–32 位，首位为英文字母，其后只允许英文字母、数字和下划线 |
| password | string | body | ✅ | 6–128 个 U+0021–U+007E ASCII 可见字符，包含全部 ASCII 可见符号；不裁剪或转换，禁止空格、换行和中文 |

请求体只接受 `username`、`password`；字段缺失、类型或格式不合法、出现额外字段时返回 422。

**响应数据**

成功状态为 HTTP 200，以下字段均必有且非空。

| 字段 | JSON 类型 | 说明 |
| --- | --- | --- |
| data.admin | object | 管理员身份信息 |
| data.admin.id | integer | 管理员主键 |
| data.admin.username | string | 数据库保存的用户名，保留大小写 |
| data.expires_at | string | UTC 到期时间，使用 ISO 8601 格式 |

**响应示例**

```json
{
  "code": "SUCCESS",
  "message": "响应成功",
  "data": {
    "admin": {
      "id": 1,
      "username": "Admin"
    },
    "expires_at": "2026-10-29T18:00:00Z"
  }
}
```

**响应 Cookie**

| 属性 | 值 |
| --- | --- |
| 名称 | `tsuki_admin_token` |
| 内容 | 管理员认证 JWT，由浏览器保存并自动携带；前端不读取其内容 |
| HttpOnly | `true` |
| SameSite | `Lax` |
| Secure | 由 `TSUKI_ADMIN_COOKIE_SECURE` 控制，默认 `true`；本机 HTTP 使用 `false` |
| Path | `/api/v1/admin` |
| Domain | 不设置，使用 Host-only Cookie |
| Max-Age | `TSUKI_ADMIN_ACCESS_TOKEN_TTL_SECONDS`，默认 `28800` 秒 |
| Expires | 签发时的 UTC 到期时间 |

有效期内浏览器重启后可继续登录，普通请求不重新签发或延长有效期。

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 401 | INVALID_CREDENTIALS |
| 403 | INVALID_CSRF_REQUEST |
| 422 | INVALID_REQUEST |

## GET /api/v1/admin/auth/me

校验 JWT 后查询管理员并核对认证版本号，返回当前身份信息。

**请求参数**

无 body、query、path 参数；通过 Cookie 请求头携带凭据。

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明（枚举须写全映射） |
| --- | --- | --- | --- | --- |
| Cookie | string | header | ✅ | 包含非空的 `tsuki_admin_token`，由浏览器在登录后自动携带 |
| X-CSRF-Protection | string | header | ❌ | GET 不校验此请求头，可不传 |

**响应数据**

成功状态为 HTTP 200，以下字段均必有且非空。

| 字段 | JSON 类型 | 说明 |
| --- | --- | --- |
| data.id | integer | 管理员主键 |
| data.username | string | 数据库保存的用户名，保留大小写 |

**响应示例**

```json
{
  "code": "SUCCESS",
  "message": "响应成功",
  "data": {
    "id": 1,
    "username": "Admin"
  }
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 401 | AUTHENTICATION_REQUIRED |
| 401 | INVALID_TOKEN |

重置密码会递增数据库中的认证版本号，旧 JWT 在下一次调用本接口或其他受保护管理接口时失效。

## POST /api/v1/admin/auth/logout

管理员退出登录，清除客户端 Cookie；不要求凭据存在或有效，重复调用幂等成功。

**请求参数**

无 body、query、path 参数。

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明（枚举须写全映射） |
| --- | --- | --- | --- | --- |
| X-CSRF-Protection | string | header | ✅ | 固定值 `1`，用于防御跨站请求伪造 |
| Cookie | string | header | ❌ | 可携带现有 `tsuki_admin_token`；缺失或 JWT 失效不影响退出 |

**响应数据**

成功状态为 HTTP 200，`data` 固定为 null。响应删除与登录时同名、同 Path 的 Cookie。

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
| 403 | INVALID_CSRF_REQUEST |

退出不撤销已签发的 JWT，已复制的令牌仍按原到期时间失效。公共响应与系统错误约定见 [统一响应与全局错误码](00-error-codes.md)。
