package com.nep.tsuki.infra.storage.s3;

import java.net.URI;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import com.nep.tsuki.common.utils.UriUtils;

import jakarta.validation.constraints.AssertTrue;
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
    @NotNull(message = "S3 uploadEndpoint 不能为空")
    private URI uploadEndpoint;
    @NotBlank(message = "S3 region 不能为空")
    private String region;
    /** 私有存储桶名（存放 staging/、originals/ 与私有 variants/） */
    @NotBlank(message = "S3 privateBucket 不能为空")
    private String privateBucket;
    /** 公开交付存储桶名（可选，随首个需要公开交付的业务启用时配置） */
    private String deliveryBucket;
    /** 公开交付访问根地址（可选，可包含路径前缀，不可含凭据、查询参数和片段） */
    private String deliveryBaseUrl;
    @NotBlank(message = "S3 accessKey 不能为空")
    private String accessKey;
    @NotBlank(message = "S3 secretKey 不能为空")
    private String secretKey;

    @AssertTrue(message = "S3 endpoint 必须是含主机、无凭据、无查询参数和片段的 HTTP(S) 根地址")
    public boolean isEndpointValid() {
        return endpoint == null || UriUtils.isHttpRootUri(endpoint);
    }

    @AssertTrue(message = "S3 uploadEndpoint 必须是含主机、无凭据、无查询参数和片段的 HTTP(S) 根地址")
    public boolean isUploadEndpointValid() {
        return uploadEndpoint == null || UriUtils.isHttpRootUri(uploadEndpoint);
    }

    @AssertTrue(message = "S3 deliveryBaseUrl 必须是含主机、无凭据、无查询参数和片段的 HTTP(S) 地址")
    public boolean isDeliveryBaseUrlValid() {
        if (!StringUtils.hasText(deliveryBaseUrl)) {
            return true;
        }
        try {
            URI uri = URI.create(deliveryBaseUrl.strip());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && StringUtils.hasText(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @AssertTrue(message = "S3 deliveryBucket 与 deliveryBaseUrl 必须成对配置或同时留空")
    public boolean isDeliveryConfigPaired() {
        return StringUtils.hasText(deliveryBucket) == StringUtils.hasText(deliveryBaseUrl);
    }

    @AssertTrue(message = "S3 deliveryBucket 不能与 privateBucket 使用同一个存储桶")
    public boolean isDeliveryBucketDistinct() {
        if (!StringUtils.hasText(privateBucket) || !StringUtils.hasText(deliveryBucket)) {
            return true;
        }
        return !privateBucket.strip().equals(deliveryBucket.strip());
    }

    /**
     * 解析规范化后的公开交付访问根地址（去除末尾多余的 "/"）
     */
    public Optional<URI> resolveDeliveryBaseUri() {
        if (!StringUtils.hasText(deliveryBaseUrl)) {
            return Optional.empty();
        }
        String normalized = deliveryBaseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return Optional.of(URI.create(normalized));
    }
}
