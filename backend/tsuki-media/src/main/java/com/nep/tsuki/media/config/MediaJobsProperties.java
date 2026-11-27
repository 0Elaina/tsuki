package com.nep.tsuki.media.config;

import java.time.Duration;
import java.util.Objects;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.nep.tsuki.media.model.enums.MediaJobType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "tsuki.media.jobs")
public class MediaJobsProperties {

    @NotNull(message = "图片处理任务租期不能为空")
    @DurationMin(seconds = 10, message = "图片处理任务租期不能小于 10 秒")
    @DurationMax(minutes = 30, message = "图片处理任务租期不能超过 30 分钟")
    private Duration imageLease = Duration.ofMinutes(2L);

    @NotNull(message = "超时任务回收间隔不能为空")
    @DurationMin(seconds = 1, message = "超时任务回收间隔不能小于 1 秒")
    @DurationMax(minutes = 5, message = "超时任务回收间隔不能超过 5 分钟")
    private Duration reapInterval = Duration.ofSeconds(10L);

    @Min(value = 1, message = "图片消费并发数不能小于 1")
    @Max(value = 16, message = "图片消费并发数不能超过 16")
    private int imageConcurrency = 1;

    /**
     * 根据任务类型获取对应的租期时长
     */
    public Duration getLeaseFor(MediaJobType jobType) {
        Objects.requireNonNull(jobType, "jobType 不能为空");
        return switch (jobType) {
            case PROCESS_IMAGE -> imageLease;
        };
    }
}
