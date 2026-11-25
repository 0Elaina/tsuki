package com.nep.tsuki.infra.storage.s3;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nep.tsuki.common.utils.UriUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 兼容对象存储客户端配置类，负责装配服务端读写客户端与预签名器
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(S3Properties.class)
public class S3ClientConfig {

    /**
     * 创建用于服务端内部读写的 {@link S3Client}
     *
     * @param s3Properties S3 连接与凭证配置
     * @return 配置完成的 S3 客户端实例
     */
    @Bean(destroyMethod = "close")
    S3Client s3Client(S3Properties s3Properties) {
        return S3Client.builder()
                .endpointOverride(UriUtils.normalizeHttpRootUri(s3Properties.getEndpoint()))
                .credentialsProvider(credentialsProvider(s3Properties))
                .region(Region.of(s3Properties.getRegion()))
                // 兼容 RustFS/MinIO 等自建对象存储，强制使用路径风格访问
                .forcePathStyle(true)
                .build();
    }

    /**
     * 创建用于签发前端直传预签名 URL 的 {@link S3Presigner}
     *
     * @param s3Properties S3 连接与凭证配置
     * @return 配置完成的 S3 预签名器实例
     */
    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(S3Properties s3Properties) {
        // 预签名 URL 同样启用路径风格，避免浏览器请求落到虚拟托管子域名
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        return S3Presigner.builder()
                // 使用面向客户端可达的 uploadEndpoint 参与签名计算
                .endpointOverride(UriUtils.normalizeHttpRootUri(s3Properties.getUploadEndpoint()))
                .credentialsProvider(credentialsProvider(s3Properties))
                .region(Region.of(s3Properties.getRegion()))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    /**
     * 根据配置构建静态访问凭证提供者
     *
     * @param s3Properties S3 连接与凭证配置
     * @return 静态凭证提供者
     */
    private static StaticCredentialsProvider credentialsProvider(S3Properties s3Properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                        s3Properties.getAccessKey(),
                        s3Properties.getSecretKey()));
    }
}