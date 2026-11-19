package com.nep.tsuki.infra.storage.s3;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(S3Properties.class)
public class S3ClientConfig {
    @Bean(destroyMethod = "close")
    S3Client s3Client(S3Properties s3Properties) {
        // 构造静态凭据
        AwsBasicCredentials credentials = AwsBasicCredentials.create(s3Properties.getAccessKey(),
                s3Properties.getSecretKey());
        return S3Client.builder()
                .endpointOverride(s3Properties.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(s3Properties.getRegion()))
                .forcePathStyle(true)
                .build();
    }
}
