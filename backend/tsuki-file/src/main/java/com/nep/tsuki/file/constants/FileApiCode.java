package com.nep.tsuki.file.constants;

import org.springframework.http.HttpStatus;

import com.nep.tsuki.common.api.ApiCode;

public final class FileApiCode {
    private FileApiCode() {
    }

    public static final ApiCode FILE_UPLOAD_RATE_LIMITED = new ApiCode(HttpStatus.TOO_MANY_REQUESTS,
            "FILE_UPLOAD_RATE_LIMITED", "上传签名请求过于频繁, 请稍候重试");
    public static final ApiCode FILE_UPLOAD_LIMIT_UNAVAILABLE = new ApiCode(HttpStatus.SERVICE_UNAVAILABLE,
            "FILE_UPLOAD_LIMIT_UNAVAILABLE", "上传限流服务暂时不可用, 请稍后再试");
    public static final ApiCode FILE_UPLOAD_EXPIRED = new ApiCode(HttpStatus.GONE, "FILE_UPLOAD_EXPIRED",
            "上传会话已过期, 请重新申请上传凭证");
    public static final ApiCode FILE_UPLOAD_OBJECT_MISSING = new ApiCode(HttpStatus.CONFLICT,
            "FILE_UPLOAD_OBJECT_MISSING", "暂存对象尚未上传完成");
    public static final ApiCode FILE_UPLOAD_METADATA_MISMATCH = new ApiCode(HttpStatus.CONFLICT,
            "FILE_UPLOAD_METADATA_MISMATCH", "已上传对象的类型或大小与声明不符");
}
