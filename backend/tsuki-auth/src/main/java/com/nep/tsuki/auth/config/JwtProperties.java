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
        @NotBlank(message = "密钥不能为空") String secret,
        @NotBlank(message = "签发者不能为空") String issuer,
        @NotNull(message = "访问令牌 TTL 不能为空") @DurationMin(minutes = 1, message = "访问令牌 TTL 至少为 1 分钟") Duration accessTtl,
        @NotNull(message = "管理端刷新令牌空闲 TTL 不能为空") @DurationMin(hours = 1, message = "管理端刷新令牌空闲 TTL 至少为 1 小时") Duration refreshAdminIdleTtl,
        @NotNull(message = "管理端刷新令牌绝对 TTL 不能为空") @DurationMin(hours = 1, message = "管理端刷新令牌绝对 TTL 至少为 1 小时") Duration refreshAdminAbsTtl,
        @NotNull(message = "用户端刷新令牌空闲 TTL 不能为空") @DurationMin(days = 1, message = "用户端刷新令牌空闲 TTL 至少为 1 天") Duration refreshUserIdleTtl,
        @NotNull(message = "用户端刷新令牌绝对 TTL 不能为空") @DurationMin(days = 1, message = "用户端刷新令牌绝对 TTL 至少为 1 天") Duration refreshUserAbsTtl) {
}
