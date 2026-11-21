package com.nep.tsuki.media.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "tsuki.media.lifecycle")
public class MediaLifecycleProperties {
    @NotNull(message = "上传中资源过期时间不能为空")
    @DurationMin(millis = 1, message = "上传中资源过期时间必须大于 0")
    private Duration uploadingTtl = Duration.ofMinutes(15);

    @NotNull(message = "待绑定资源有效时间不能为空")
    @DurationMin(millis = 1, message = "待绑定资源有效时间必须大于 0")
    private Duration availableTtl = Duration.ofHours(24);
}
