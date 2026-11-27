package com.nep.tsuki.file.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nep.tsuki.file.storage.FileObjectStorage;
import com.nep.tsuki.file.storage.StagedUploadStorage;
import com.nep.tsuki.file.storage.s3.S3FileObjectStorage;
import com.nep.tsuki.file.storage.s3.S3StagedUploadStorage;
import com.nep.tsuki.infra.storage.s3.S3Properties;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileUploadProperties.class)
public class FileStorageConfig {

    /**
     * 注册基于 S3 的暂存上传存储实现
     */
    @Bean
    StagedUploadStorage stagedUploadStorage(
            S3Client s3Client,
            S3Presigner s3Presigner,
            S3Properties s3Properties,
            FileUploadProperties fileUploadProperties) {
        return new S3StagedUploadStorage(
                s3Client,
                s3Presigner,
                s3Properties,
                fileUploadProperties);
    }

    /**
     * 注册基于 S3 兼容协议的文件对象存储实现
     */
    @Bean
    FileObjectStorage fileObjectStorage(S3Client s3Client, S3Properties s3Properties) {
        return new S3FileObjectStorage(s3Client, s3Properties);
    }
}
