# 统一响应与全局错误码

## JSON 业务响应

JSON 业务响应统一包含以下字段，具体 `data` 结构由各接口定义。实现模型见 [responses.py](../../backend/src/tsuki/api/responses.py)。

| 字段 | 类型 | 必有 | 说明 |
| --- | --- | --- | --- |
| `code` | string | 是 | 成功为 `SUCCESS`；失败为具名业务码 |
| `message` | string | 是 | 可展示说明；成功默认为“响应成功” |
| `data` | T 或 null | 是 | 业务数据；无数据时为 null；请求校验失败时包含校验错误数据 |

**无数据的成功响应示例**

```json
{
  "code": "SUCCESS",
  "message": "响应成功",
  "data": null
}
```

HTTP 状态码表达请求结果，正文业务码表达具体原因。业务失败保留 4xx 或 5xx，成功状态由各接口声明。

OpenAPI、交互式文档、文件、流式响应、无正文响应和 CORS 协议层响应不套用业务封装。

## 路由错误声明约定

路由处理函数只声明成功返回值（如 `-> ApiResponse[T]`），严禁在各个路由装饰器上机械重复声明 401、422、500 等通用错误响应。通用错误与参数校验失败统一由全局异常拦截器兜底封装；各接口特异性业务错误在接口设计文档与本文档中集中维护，前端基于文档契约进行错误处理，不依赖 OpenAPI 逐个声明通用错误。

## 请求校验错误数据

请求参数校验失败返回 HTTP 422、`code=INVALID_REQUEST`，`data` 类型为 `ValidationErrorData`。

| 模型 | 字段 | 类型 | 必有 | 说明 |
| --- | --- | --- | --- | --- |
| `ValidationErrorData` | `errors` | array of ValidationIssue | 是 | 请求校验错误列表 |
| `ValidationIssue` | `location` | array of (string 或 integer) | 是 | 路径元素可混合字符串与整数，保留参数位置、字段名与数组下标 |
| `ValidationIssue` | `message` | string | 是 | 对应校验错误的 `msg` |
| `ValidationIssue` | `type` | string | 是 | 对应校验错误的 `type` |

响应不包含原始请求值及内部异常上下文。校验消息来自框架或字段校验器。主动抛出的框架 HTTP 422 异常同样使用 `INVALID_REQUEST`，但其 `data` 为 null。

`location` 的首项通常是 `body`、`query`、`path` 或 `header`，后续项表示字段和数组下标，例如 `["body", "username"]`。该结构是本项目的实际校验错误契约，与 FastAPI 默认的 `detail` 结构不同。

## 全局错误码

定义来源为 [core/api_code.py](../../backend/src/tsuki/core/api_code.py) 的 `CommonApiCode`。

| HTTP 状态码 | 错误码 | 默认 message | data | 触发条件 |
| --- | --- | --- | --- | --- |
| 422 | `INVALID_REQUEST` | 请求参数校验失败 | ValidationErrorData 或 null | FastAPI 请求校验失败，或框架 HTTP 422 异常 |
| 404 | `RESOURCE_NOT_FOUND` | 请求的资源不存在 | null | 框架 HTTP 404 异常；业务也可复用此定义 |
| 405 | `METHOD_NOT_ALLOWED` | 请求方法不允许 | null | 框架 HTTP 405 异常 |
| 原始 HTTP 状态码 | `HTTP_ERROR` | 请求失败 | null | 404、405、422 之外的框架 HTTP 异常 |
| 500 | `INTERNAL_SERVER_ERROR` | 服务器内部错误 | null | 未处理的系统异常，包含响应模型校验失败 |

`HTTP_ERROR` 保留原始 HTTP 状态和响应头。框架 HTTP 异常使用上表默认消息；普通业务异常的 `data` 为 null，`message` 使用业务提供的非空说明或错误码默认说明。

业务错误码按对应功能实现补充，采用全大写字母与下划线、动词或形容词加名词命名，在本文件集中归档；模块接口文档列出各接口可能返回的错误码。

## 认证业务错误码

定义来源为 [auth/api_code.py](../../backend/src/tsuki/modules/auth/api_code.py) 的 `AuthApiCode`。

| HTTP 状态码 | 错误码 | 默认 message | data | 触发条件 |
| --- | --- | --- | --- | --- |
| 401 | `INVALID_CREDENTIALS` | 用户名或密码错误 | null | 登录时账号不存在或密码不匹配 |
| 401 | `AUTHENTICATION_REQUIRED` | 请先登录 | null | 访问受保护接口未携带认证 Cookie，或其值为空 |
| 401 | `INVALID_TOKEN` | 登录已失效，请重新登录 | null | JWT 签名或必需声明无效、过期、账号不存在或认证版本号不匹配 |
| 403 | `INVALID_CSRF_REQUEST` | 请求校验失败 | null | 非安全 HTTP 方法未携带正确的 `X-CSRF-Protection: 1` 头 |
| 409 | `ADMIN_ALREADY_EXISTS` | 管理员已存在 | null | 创建管理员命令遇到同名账号；当前不通过 HTTP 接口触发 |
| 404 | `ADMIN_NOT_FOUND` | 管理员不存在 | null | 重置密码命令未找到目标账号；当前不通过 HTTP 接口触发 |

后两项仅用于管理员命令，表中的状态为错误定义属性，不产生 HTTP 响应；命令用法见 [工程指南](../05-engineering-guide.md#管理员维护)。登录、当前身份和退出接口的对外错误码见 [管理员认证 API](01-auth-api.md)。

## 作品业务错误码

定义来源为 [work/api_code.py](../../backend/src/tsuki/modules/work/api_code.py) 的 `WorkApiCode`。

| HTTP 状态码 | 错误码 | 默认 message | data | 触发条件 |
| --- | --- | --- | --- | --- |
| 404 | `WORK_NOT_FOUND` | 作品不存在 | null | 查询、修改或删除作品时指定 ID 不存在，或批量操作中任一目标作品不存在 |

接口对外错误码见 [作品管理 API](02-work-api.md)。

## 系统异常

系统异常返回固定说明，响应不包含堆栈。开启框架调试模式后，系统异常可返回调试回溯，此时不保证 500 业务封装；本机配置来源见 [工程指南](../05-engineering-guide.md#配置来源)。

配置读取和应用启动期间的错误直接导致启动失败，不产生 HTTP 业务响应。
