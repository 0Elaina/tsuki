# 认证模块 API

管理端与用户端为两套隔离的账号体系，按入口独立校验角色、签发 Refresh Cookie 与计数限流，跨端不可混用；目前管理端角色仅 `ADMIN`，用户端角色仅 `USER`。

## GET /api/v1/admin/auth/csrf

获取 CSRF 防伪令牌并下发 `XSRF-TOKEN` Cookie（`Path=/api/v1`，`HttpOnly`，`SameSite=Strict`，非纯 `dev` 环境启用 `Secure`）。

**请求参数**

无请求参数

**响应示例**

```json
{
  "data": {
    "token": "csrf-token-value",
    "headerName": "X-XSRF-TOKEN"
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

**错误码**

无接口专属错误码，公共错误见[全局错误码](00-error-codes.md)。

## POST /api/v1/admin/auth/login

校验管理员账号密码与 CSRF 请求头，签发 Access JWT，通过 `HttpOnly` Cookie（`tsuki_admin_refresh`，`Path=/api/v1/admin/auth`）下发 Refresh JWT 并清除 CSRF Cookie。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| X-XSRF-TOKEN | string | header | ✅ | `GET /api/v1/admin/auth/csrf` 返回的 CSRF 令牌（默认头名） |
| username | string | body | ✅ | 管理员用户名，非空白且不超过 32 字符 |
| password | string | body | ✅ | 登录密码，非空白且不超过 72 字符 |

**响应示例**

```json
{
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 900
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 400 | VALIDATION_FAILED |
| 401 | INVALID_CREDENTIALS |
| 403 | ACCOUNT_DISABLED |
| 403 | ACCESS_DENIED |
| 429 | LOGIN_RATE_LIMITED |
| 503 | LOGIN_UNAVAILABLE |

## POST /api/v1/admin/auth/refresh

校验 CSRF 请求头与 `tsuki_admin_refresh` Cookie，轮换签发新的 Access/Refresh JWT；遇到 `INVALID_REFRESH_TOKEN` 时清除该 Cookie。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| X-XSRF-TOKEN | string | header | ✅ | `GET /api/v1/admin/auth/csrf` 返回的 CSRF 令牌（默认头名） |
| tsuki_admin_refresh | string | cookie | ❌ | 管理员刷新令牌 Cookie（缺失时返回 `401 INVALID_REFRESH_TOKEN`） |

**响应示例**

```json
{
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 900
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 401 | INVALID_REFRESH_TOKEN |
| 403 | ACCESS_DENIED |

## POST /api/v1/admin/auth/logout

校验 CSRF 请求头，撤销当前刷新会话并清除 `tsuki_admin_refresh` 与 `XSRF-TOKEN` Cookie；Cookie 缺失或已失效仍视为退出成功。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| X-XSRF-TOKEN | string | header | ✅ | `GET /api/v1/admin/auth/csrf` 返回的 CSRF 令牌（默认头名） |
| tsuki_admin_refresh | string | cookie | ❌ | 管理员刷新令牌 Cookie |

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
| 403 | ACCESS_DENIED |

## GET /api/v1/auth/csrf

获取 CSRF 防伪令牌并下发 `XSRF-TOKEN` Cookie（`Path=/api/v1`，`HttpOnly`，`SameSite=Strict`，非纯 `dev` 环境启用 `Secure`）。

**请求参数**

无请求参数

**响应示例**

```json
{
  "data": {
    "token": "csrf-token-value",
    "headerName": "X-XSRF-TOKEN"
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

**错误码**

无接口专属错误码，公共错误见[全局错误码](00-error-codes.md)。

## POST /api/v1/auth/login

校验普通用户账号密码与 CSRF 请求头，签发 Access JWT，通过 `HttpOnly` Cookie（`tsuki_user_refresh`，`Path=/api/v1/auth`）下发 Refresh JWT 并清除 CSRF Cookie。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| X-XSRF-TOKEN | string | header | ✅ | `GET /api/v1/auth/csrf` 返回的 CSRF 令牌（默认头名） |
| username | string | body | ✅ | 用户名，非空白且不超过 32 字符 |
| password | string | body | ✅ | 登录密码，非空白且不超过 72 字符 |

**响应示例**

```json
{
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 900
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 400 | VALIDATION_FAILED |
| 401 | INVALID_CREDENTIALS |
| 403 | ACCOUNT_DISABLED |
| 403 | ACCESS_DENIED |
| 429 | LOGIN_RATE_LIMITED |
| 503 | LOGIN_UNAVAILABLE |

## POST /api/v1/auth/refresh

校验 CSRF 请求头与 `tsuki_user_refresh` Cookie，轮换签发新的 Access/Refresh JWT；遇到 `INVALID_REFRESH_TOKEN` 时清除该 Cookie。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| X-XSRF-TOKEN | string | header | ✅ | `GET /api/v1/auth/csrf` 返回的 CSRF 令牌（默认头名） |
| tsuki_user_refresh | string | cookie | ❌ | 用户刷新令牌 Cookie（缺失时返回 `401 INVALID_REFRESH_TOKEN`） |

**响应示例**

```json
{
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 900
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 401 | INVALID_REFRESH_TOKEN |
| 403 | ACCESS_DENIED |

## POST /api/v1/auth/logout

校验 CSRF 请求头，撤销当前刷新会话并清除 `tsuki_user_refresh` 与 `XSRF-TOKEN` Cookie；Cookie 缺失或已失效仍视为退出成功。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| X-XSRF-TOKEN | string | header | ✅ | `GET /api/v1/auth/csrf` 返回的 CSRF 令牌（默认头名） |
| tsuki_user_refresh | string | cookie | ❌ | 用户刷新令牌 Cookie |

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
| 403 | ACCESS_DENIED |

