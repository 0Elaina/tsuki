package com.nep.tsuki.media.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nep.tsuki.infra.storage.s3.S3Properties;
import com.nep.tsuki.media.storage.MediaObjectStorage;
import com.nep.tsuki.media.storage.StagedUploadStorage;
import com.nep.tsuki.media.storage.s3.S3MediaObjectStorage;
import com.nep.tsuki.media.storage.s3.S3StagedUploadStorage;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaUploadProperties.class)
public class MediaStorageConfig {

    /**
     * 注册基于 S3 的暂存上传存储实现
     *
     * @param s3Client              S3 同步客户端，用于执行对象元数据查询与管理操作
     * @param s3Presigner           S3 预签名生成器，用于签发客户端直传凭证
     * @param s3Properties          S3 基础连接与存储桶配置属性
     * @param mediaUploadProperties 媒体上传业务规则配置属性
     * @return 基于 S3 的 {@link StagedUploadStorage} 实例
     */
    @Bean
    StagedUploadStorage stagedUploadStorage(
            S3Client s3Client,
            S3Presigner s3Presigner,
            S3Properties s3Properties,
            MediaUploadProperties mediaUploadProperties) {
        return new S3StagedUploadStorage(
                s3Client,
                s3Presigner,
                s3Properties,
                mediaUploadProperties);
    }

    /**
     * 注册基于 S3 兼容协议的媒体对象存储实现
     *
     * @param s3Client     AWS S3 同步客户端实例
     * @param s3Properties 对象存储连接与桶配置属性
     * @return 媒体对象存储接口实例
     */
    @Bean
    MediaObjectStorage mediaObjectStorage(S3Client s3Client, S3Properties s3Properties) {
        return new S3MediaObjectStorage(s3Client, s3Properties);
    }
}
