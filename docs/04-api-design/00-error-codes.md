# 全局错误码

HTTP 状态表示请求结果，`code` 是供调用方判断的稳定字符串，`message` 是可展示的提示。统一响应包含 `data`、`code`、`message`、`errors`。普通错误的 `data` 为 `null`。成功和普通错误的 `errors` 为 `[]`。Spring Boot 的 `/error` 回退使用框架默认格式。

| HTTP 状态 | 错误码 | 默认提示 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 请求参数校验失败 |
| 400 | `BAD_REQUEST` | 请求格式不正确 |
| 401 | `UNAUTHENTICATED` | 未认证或凭据无效 |
| 403 | `ACCESS_DENIED` | 无权限访问该资源 |
| 404 | `NOT_FOUND` | 请求的资源不存在 |
| 405 | `METHOD_NOT_ALLOWED` | 请求方法不受支持 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 请求内容类型不受支持 |
| 其他框架 4xx | `REQUEST_FAILED` | 请求处理失败 |
| 500 或框架其他 5xx | `INTERNAL_SERVER_ERROR` | 服务器内部错误 |

## 字段校验错误

`VALIDATION_FAILED` 的 `data` 为 `null`。`errors` 是由 `field` 和 `message` 组成的数组。`field` 使用字段名或嵌套字段路径，直接约束方法参数时使用 Java 参数名。表单级问题的 `field` 为 `null`，同一字段或表单级问题保留首条提示。

```json
{
  "data": null,
  "code": "VALIDATION_FAILED",
  "message": "请求参数校验失败",
  "errors": [
    {
      "field": "size",
      "message": "页大小不能超过 100"
    }
  ]
}
```

## 业务错误码

| HTTP 状态 | 错误码 | 默认提示 | 所属模块 |
| --- | --- | --- | --- |
| 401 | `INVALID_CREDENTIALS` | 非法的凭据 | `tsuki-user` |
| 403 | `ACCOUNT_DISABLED` | 账户已停用 | `tsuki-user` |
| 404 | `ACCOUNT_NOT_FOUND` | 账户不存在 | `tsuki-user` |
| 401 | `INVALID_REFRESH_TOKEN` | 刷新令牌无效或已过期 | `tsuki-auth` |
| 429 | `LOGIN_RATE_LIMITED` | 登录请求过于频繁, 请稍候重试 | `tsuki-auth` |
| 503 | `LOGIN_UNAVAILABLE` | 登录服务暂时不可用, 请稍后再试 | `tsuki-auth` |
