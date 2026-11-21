package com.nep.tsuki.infra.storage.s3;

import java.net.URI;
import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "tsuki.s3")
public class S3Properties {
    @NotNull(message = "S3 endpoint 不能为空")
    private URI endpoint;
    @NotBlank(message = "S3 region 不能为空")
    private String region;
    @NotBlank(message = "S3 bucket 不能为空")
    private String bucket;
    @NotBlank(message = "S3 accessKey 不能为空")
    private String accessKey;
    @NotBlank(message = "S3 secretKey 不能为空")
    private String secretKey;
    @NotNull(message = "S3 apiCallTimeout 不能为空")
    @DurationMin(millis = 1, message = "S3 apiCallTimeout 必须大于 0")
    private Duration apiCallTimeout = Duration.ofSeconds(10);
    @NotNull(message = "S3 apiCallAttemptTimeout 不能为空")
    @DurationMin(millis = 1, message = "S3 apiCallAttemptTimeout 必须大于 0")
    private Duration apiCallAttemptTimeout = Duration.ofSeconds(3);
}
