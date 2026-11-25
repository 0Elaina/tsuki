package com.nep.tsuki.infra.storage.s3;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
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
    @NotBlank(message = "S3 bucket 不能为空")
    private String bucket;
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
}
