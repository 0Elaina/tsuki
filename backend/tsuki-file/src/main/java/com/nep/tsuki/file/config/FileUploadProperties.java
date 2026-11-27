package com.nep.tsuki.file.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "tsuki.file.upload")
public class FileUploadProperties {

    @NotNull(message = "上传签名有效期不能为空")
    @DurationMin(minutes = 1, message = "上传签名有效期不能小于 1 分钟")
    @DurationMax(minutes = 15, message = "上传签名有效期不能超过 15 分钟")
    private Duration signatureTtl = Duration.ofMinutes(10L);

    @Positive(message = "图片最大字节数必须大于 0")
    private long maxImageBytes = 10_485_760L;

    @Positive(message = "管理端签发限额必须大于 0")
    private long adminSignLimit = 60L;

    @Positive(message = "用户端签发限额必须大于 0")
    private long userSignLimit = 10L;
}
