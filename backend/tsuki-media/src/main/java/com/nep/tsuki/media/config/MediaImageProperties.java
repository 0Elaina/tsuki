package com.nep.tsuki.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "tsuki.media.image")
public class MediaImageProperties {
    @NotBlank(message = "图片对象键前缀不能为空")
    private String keyPrefix = "tsuki/images/";

    @Min(value = 1, message = "图片单边最大像素必须大于 0")
    private int maxDimension = 6144;

    @Min(value = 1, message = "图片最大总像素必须大于 0")
    private long maxPixels = 25_000_000L;

    @DecimalMin(value = "0.1", message = "JPEG 压缩质量不能低于 0.1")
    @DecimalMax(value = "1.0", message = "JPEG 压缩质量不能高于 1.0")
    private double jpegQuality = 0.85;
}
