package com.nep.tsuki.file.config;

import java.time.Duration;
import java.util.Objects;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.nep.tsuki.file.model.enums.FileJobType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "tsuki.file.jobs")
public class FileJobsProperties {

    @NotNull(message = "上传校验任务租期不能为空")
    @DurationMin(seconds = 10, message = "上传校验任务租期不能小于 10 秒")
    @DurationMax(minutes = 30, message = "上传校验任务租期不能超过 30 分钟")
    private Duration validateLease = Duration.ofMinutes(2L);

    @NotNull(message = "衍生物生成任务租期不能为空")
    @DurationMin(seconds = 10, message = "衍生物生成任务租期不能小于 10 秒")
    @DurationMax(minutes = 30, message = "衍生物生成任务租期不能超过 30 分钟")
    private Duration variantLease = Duration.ofMinutes(3L);

    @NotNull(message = "对象清理任务租期不能为空")
    @DurationMin(seconds = 10, message = "对象清理任务租期不能小于 10 秒")
    @DurationMax(minutes = 30, message = "对象清理任务租期不能超过 30 分钟")
    private Duration cleanupLease = Duration.ofMinutes(2L);

    @NotNull(message = "超时任务回收间隔不能为空")
    @DurationMin(seconds = 1, message = "超时任务回收间隔不能小于 1 秒")
    @DurationMax(minutes = 5, message = "超时任务回收间隔不能超过 5 分钟")
    private Duration reapInterval = Duration.ofSeconds(10L);

    @Min(value = 1, message = "交互任务消费并发数不能小于 1")
    @Max(value = 16, message = "交互任务消费并发数不能超过 16")
    private int interactiveConcurrency = 2;

    @Min(value = 1, message = "清理任务消费并发数不能小于 1")
    @Max(value = 16, message = "清理任务消费并发数不能超过 16")
    private int cleanupConcurrency = 1;

    /**
     * 根据任务类型获取对应的执行租期时长
     */
    public Duration getLeaseFor(FileJobType jobType) {
        Objects.requireNonNull(jobType, "jobType 不能为空");
        return switch (jobType) {
            case VALIDATE_UPLOAD -> validateLease;
            case GENERATE_VARIANT -> variantLease;
            case CLEANUP_ASSET, CLEANUP_VARIANT -> cleanupLease;
        };
    }
}
