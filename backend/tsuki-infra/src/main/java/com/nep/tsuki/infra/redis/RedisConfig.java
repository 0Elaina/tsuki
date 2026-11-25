package com.nep.tsuki.infra.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    @Bean
    RedisScript<Long> fixedWindowRateLimitScript() {
        return RedisScript.of(new ClassPathResource("lua/fixed_window_rate_limit.lua"), Long.class);
    }
}
