package com.nep.tsuki.auth.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.nep.tsuki.auth.model.enums.AccountType;
import com.nep.tsuki.auth.security.RestAccessDeniedHandler;
import com.nep.tsuki.auth.security.RestAuthenticationEntryPoint;
import com.nep.tsuki.auth.utils.CookieUtils;
import com.nep.tsuki.constants.EnvConstants;

import jakarta.servlet.DispatcherType;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    CookieCsrfTokenRepository cookieCsrfTokenRepository(Environment environment) {
        CookieCsrfTokenRepository cookieCsrfTokenRepository = new CookieCsrfTokenRepository();
        cookieCsrfTokenRepository.setCookiePath("/api/v1");
        cookieCsrfTokenRepository.setCookieCustomizer(
                c -> c.sameSite(SameSite.STRICT.attributeValue())
                        .secure(CookieUtils.isCookieSecure(environment)));
        return cookieCsrfTokenRepository;
    }

    // 认证入口与 CSRF 链
    @Bean
    @Order(1)
    SecurityFilterChain authSecurityFilterChain(HttpSecurity http,
            CookieCsrfTokenRepository cookieCsrfTokenRepository,
            RestAccessDeniedHandler restAccessDeniedHandler,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint) {
        return http
                .securityMatchers(m -> m.requestMatchers(HttpMethod.GET, "/api/v1/admin/auth/csrf")
                        .requestMatchers(
                                HttpMethod.POST, "/api/v1/admin/auth/login",
                                "/api/v1/admin/auth/refresh",
                                "/api/v1/admin/auth/logout"))
                .csrf(csrf -> csrf.csrfTokenRepository(cookieCsrfTokenRepository))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .build();
    }

    // 业务资源链
    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, RestAccessDeniedHandler restAccessDeniedHandler,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint, Environment environment,
            @Qualifier("accessJwtDecoder") JwtDecoder accessJwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    if (environment.acceptsProfiles(Profiles.of(EnvConstants.DEV))) {
                        auth.requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                                .permitAll();
                    }
                    auth.requestMatchers("/api/v1/admin/**").hasRole(AccountType.ADMIN.name());
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    auth.anyRequest().denyAll();
                })
                .oauth2ResourceServer(o -> o.jwt(
                        jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                                .decoder(accessJwtDecoder))
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(
                                restAccessDeniedHandler))
                .build();
    }
}
