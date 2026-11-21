package com.nep.tsuki.media.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ PublicMediaStorageProperties.class, MediaImageProperties.class })
public class MediaConfig {

}
