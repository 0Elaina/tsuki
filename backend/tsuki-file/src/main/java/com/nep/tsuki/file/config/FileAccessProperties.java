package com.nep.tsuki.file.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "tsuki.file.access")
public class FileAccessProperties {

    @NotNull(message = "私有文件访问签名有效期不能为空")
    @DurationMin(seconds = 30, message = "私有文件访问签名有效期不能小于 30 秒")
    @DurationMax(hours = 1, message = "私有文件访问签名有效期不能超过 1 小时")
    private Duration presignedGetTtl = Duration.ofMinutes(5L);
}
