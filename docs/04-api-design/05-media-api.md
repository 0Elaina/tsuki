# 媒体模块 API

## POST /api/v1/admin/media/images

校验管理端 Bearer Access JWT（`ROLE_ADMIN`），校验并重编码上传的基础图片（支持 JPEG、PNG 与静态 WebP），写入公开媒体存储后返回资源 ID 与公开访问地址（HTTP `201 Created`）。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Authorization | string | header | ✅ | Bearer 认证头，格式为 `Bearer <accessToken>` |
| file | file | body (`multipart/form-data`) | ✅ | 图片文件；默认单文件上限 `10MB`、请求上限 `11MB`，单边不超过 `6144 px`、总像素不超过 `25,000,000`；JPEG 按配置质量（默认 `0.85`）结合 EXIF 朝向重编码，PNG 与静态 WebP 保留透明度重编码为 PNG |

**响应示例**

```json
{
  "data": {
    "assetId": "42",
    "url": "http://127.0.0.1:9000/tsuki-public-media/tsuki/images/42/base-550e8400-e29b-41d4-a716-446655440000.png",
    "contentType": "image/png",
    "width": 1024,
    "height": 1024,
    "expiresAt": "2026-11-22T14:00:00.123456Z"
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 400 | BAD_REQUEST |
| 400 | INVALID_IMAGE_FILE |
| 401 | UNAUTHENTICATED |
| 403 | ACCESS_DENIED |
| 413 | PAYLOAD_TOO_LARGE |
| 415 | UNSUPPORTED_MEDIA_TYPE |
| 503 | MEDIA_STORAGE_UNAVAILABLE |
