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
            "FILE_UPLOAD_METADATA_MISMATCH", "已上传对象的类型、大小或校验信息与声明不符");
    public static final ApiCode FILE_ASSET_NOT_FOUND = new ApiCode(HttpStatus.NOT_FOUND,
            "FILE_ASSET_NOT_FOUND", "文件资产不存在");
    public static final ApiCode FILE_ASSET_NOT_READY = new ApiCode(HttpStatus.CONFLICT,
            "FILE_ASSET_NOT_READY", "文件资产尚未就绪或不可用");
    public static final ApiCode FILE_ASSET_OWNER_CONFLICT = new ApiCode(HttpStatus.CONFLICT,
            "FILE_ASSET_OWNER_CONFLICT", "文件资产已被其他业务记录绑定");
    public static final ApiCode FILE_ASSET_EXPIRED = new ApiCode(HttpStatus.GONE,
            "FILE_ASSET_EXPIRED", "文件资产未及时绑定已过期");
    public static final ApiCode FILE_ASSET_NOT_BOUND = new ApiCode(HttpStatus.CONFLICT,
            "FILE_ASSET_NOT_BOUND", "文件资产尚未绑定业务记录");
    public static final ApiCode FILE_VARIANT_NOT_FOUND = new ApiCode(HttpStatus.NOT_FOUND,
            "FILE_VARIANT_NOT_FOUND", "文件衍生物不存在");
    public static final ApiCode FILE_VARIANT_NOT_READY = new ApiCode(HttpStatus.CONFLICT,
            "FILE_VARIANT_NOT_READY", "文件衍生物尚未就绪或已撤回");
    public static final ApiCode FILE_VARIANT_NOT_RETRYABLE = new ApiCode(HttpStatus.CONFLICT,
            "FILE_VARIANT_NOT_RETRYABLE", "仅失败状态的文件衍生物允许重试");
}
