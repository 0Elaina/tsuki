# 艺人模块 API

## POST /api/v1/admin/artists

校验管理端 Bearer Access JWT（`ROLE_ADMIN`），创建艺人档案、按需绑定头像与写入扩展资料及有序别名，并返回完整详情（HTTP `201 Created`）。

**请求参数**

| 字段 | 类型 | 位置(body/query/path/header) | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Authorization | string | header | ✅ | Bearer 认证头，格式为 `Bearer <accessToken>` |
| name | string | body | ✅ | 艺人展示名，非空白且长度为 1~128 个字符，保存前去除首尾空白，允许同名档案 |
| sortName | string | body | ❌ | 排序名，最多 128 个字符，去首尾空白后为空白时存为 `null` |
| type | string | body | ✅ | 艺人类型枚举名称：`PERSON`=个人，`GROUP`=团体，`VIRTUAL`=虚拟艺人，`UNKNOWN`=未知（仅接收大写字符串，不接收数字枚举） |
| countryCode | string | body | ❌ | 主要关联国家或地区代码（ISO 3166-1 alpha-2），最多 16 字符，去首尾空白后转为大写校验；为空白时存为 `null` |
| debutYear | int | body | ❌ | 出道年份，范围为 `1` 到上海时区当前年份 |
| officialWebsiteUrl | string | body | ❌ | 艺人官方网站，去首尾空白后最多 2048 字符，须为无用户凭据、含合法 host 的 HTTP/HTTPS URL；为空白时存为 `null` |
| avatarAssetId | long | body | ❌ | 预上传图片资源 ID（正整数，Java `Long` 范围，支持数字或十进制字符串）；为空表示无头像 |
| biography | string | body | ❌ | 纯文本简介，最多 4000 个字符，去首尾空白并保留内容内部换行，为空白时存为 `null` |
| aliases | string[] | body | ❌ | 有序别名列表，省略或 `null` 按 `[]` 处理，最多 20 项；每项非空白且长度 1~128 个字符，去首尾空白后大小写不敏感不可重复 |

**响应示例**

```json
{
  "data": {
    "id": "1",
    "name": "The Beatles",
    "sortName": "Beatles, The",
    "type": "GROUP",
    "countryCode": "GB",
    "debutYear": 1960,
    "officialWebsiteUrl": "https://www.thebeatles.com",
    "avatarAssetId": "42",
    "avatarUrl": "http://127.0.0.1:9000/tsuki-public-media/tsuki/images/42/base-550e8400-e29b-41d4-a716-446655440000.png",
    "biography": "English rock band formed in Liverpool.\nActive 1960-1970.",
    "aliases": [
      "Fab Four",
      "ザ・ビートルズ"
    ],
    "createdAt": "2026-11-19T11:00:00.123456Z",
    "updatedAt": "2026-11-19T11:00:00.123456Z"
  },
  "code": "SUCCESS",
  "message": "响应成功",
  "errors": []
}
```

`type` 枚举取值：`PERSON`（个人）、`GROUP`（团体）、`VIRTUAL`（虚拟艺人）、`UNKNOWN`（未知）。

**错误码**

| 状态码 | 错误码 |
| --- | --- |
| 400 | BAD_REQUEST |
| 400 | VALIDATION_FAILED |
| 400 | INVALID_COUNTRY_CODE |
| 400 | INVALID_URL |
| 400 | INVALID_ARTIST_DEBUT_YEAR |
| 400 | DUPLICATE_ARTIST_ALIAS |
| 401 | UNAUTHENTICATED |
| 403 | ACCESS_DENIED |
| 409 | MEDIA_ASSET_NOT_BINDABLE |
