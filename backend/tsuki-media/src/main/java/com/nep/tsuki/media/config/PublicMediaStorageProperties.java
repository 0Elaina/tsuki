package com.nep.tsuki.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "tsuki.media.storage.public-media")
public class PublicMediaStorageProperties {
    @NotBlank(message = "公开媒体存储桶不能为空")
    private String bucket;

    @NotBlank(message = "公开媒体基地址不能为空")
    private String publicBaseUrl;
}
