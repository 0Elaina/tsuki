# 全局错误码

HTTP 状态表示请求结果，`code` 是供调用方判断的稳定字符串，`message` 是可展示的提示。统一响应包含 `data`、`code`、`message`、`errors`。普通错误的 `data` 为 `null`，`errors` 为 `{}`。成功响应的 `errors` 也为 `{}`。Spring Boot 的 `/error` 回退使用框架默认格式。

| HTTP 状态 | 错误码 | 默认提示 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 请求参数校验失败 |
| 400 | `BAD_REQUEST` | 请求格式不正确 |
| 404 | `NOT_FOUND` | 请求的资源不存在 |
| 405 | `METHOD_NOT_ALLOWED` | 请求方法不受支持 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 请求内容类型不受支持 |
| 其他框架 4xx | `REQUEST_FAILED` | 请求处理失败 |
| 500 或框架其他 5xx | `INTERNAL_SERVER_ERROR` | 服务器内部错误 |

## 字段校验错误

`VALIDATION_FAILED` 的 `data` 为 `null`。`errors` 的键是字段名或嵌套字段路径，值是该字段的第一条提示。直接约束方法参数时使用 Java 参数名，表单级问题使用 `_global`。

```json
{
  "data": null,
  "code": "VALIDATION_FAILED",
  "message": "请求参数校验失败",
  "errors": {
    "size": "页大小不能超过 100"
  }
}
```
