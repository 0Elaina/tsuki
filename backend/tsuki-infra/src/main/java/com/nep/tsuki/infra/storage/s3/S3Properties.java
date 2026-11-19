package com.nep.tsuki.infra.storage.s3;

import java.net.URI;

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
}
