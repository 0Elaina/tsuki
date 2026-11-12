package com.nep.tsuki.auth.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "tsuki.auth")
public record JwtProperties(
                @NotBlank(message = "访问密钥不能为空") String accessSecret,
                @NotBlank(message = "签发者不能为空") String issuer,
                @NotBlank(message = "管理端受众不能为空") String adminAud,
                @NotNull(message = "访问令牌 TTL 不能为空") @DurationMin(minutes = 1, message = "访问令牌 TTL 至少为 1 分钟") Duration accessTtl) {

}
