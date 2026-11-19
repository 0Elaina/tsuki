package com.nep.tsuki.auth.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "tsuki.auth")
public class JwtProperties {
    @NotBlank(message = "密钥不能为空")
    private String secret;
    @NotBlank(message = "签发者不能为空")
    private String issuer;
    @NotNull(message = "访问令牌 TTL 不能为空")
    @DurationMin(minutes = 1, message = "访问令牌 TTL 至少为 1 分钟")
    private Duration accessTtl;
    @NotNull(message = "管理端刷新令牌空闲 TTL 不能为空")
    @DurationMin(hours = 1, message = "管理端刷新令牌空闲 TTL 至少为 1 小时")
    private Duration refreshAdminIdleTtl;
    @NotNull(message = "管理端刷新令牌绝对 TTL 不能为空")
    @DurationMin(hours = 1, message = "管理端刷新令牌绝对 TTL 至少为 1 小时")
    private Duration refreshAdminAbsTtl;
    @NotNull(message = "用户端刷新令牌空闲 TTL 不能为空")
    @DurationMin(days = 1, message = "用户端刷新令牌空闲 TTL 至少为 1 天")
    private Duration refreshUserIdleTtl;
    @NotNull(message = "用户端刷新令牌绝对 TTL 不能为空")
    @DurationMin(days = 1, message = "用户端刷新令牌绝对 TTL 至少为 1 天")
    private Duration refreshUserAbsTtl;
}
