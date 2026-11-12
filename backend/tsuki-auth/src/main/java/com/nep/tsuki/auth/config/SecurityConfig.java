package com.nep.tsuki.auth.config;

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

import com.nep.tsuki.auth.model.enums.AccountType;
import com.nep.tsuki.auth.security.RestAccessDeniedHandler;
import com.nep.tsuki.auth.security.RestAuthenticationEntryPoint;
import com.nep.tsuki.constants.EnvConstants;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

        @Bean
        @Order(1)
        SecurityFilterChain adminSecurityFilterChain(HttpSecurity http, RestAccessDeniedHandler restAccessDeniedHandler,
                        RestAuthenticationEntryPoint restAuthenticationEntryPoint, Environment environment,
                        JwtDecoder jwtDecoder,
                        JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
                return http
                                .csrf(csrf -> csrf.disable())
                                .formLogin(form -> form.disable())
                                .httpBasic(basic -> basic.disable())
                                .logout(logout -> logout.disable())
                                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> {
                                        auth.requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll();
                                        if (environment.acceptsProfiles(Profiles.of(EnvConstants.DEV))) {
                                                auth.requestMatchers(
                                                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                                                                .permitAll();
                                        }
                                        auth.requestMatchers("/api/v1/admin/**").hasRole(AccountType.ADMIN.name());
                                        auth.anyRequest().denyAll();
                                })
                                .oauth2ResourceServer(o -> o.jwt(
                                                jwt -> jwt
                                                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                                                                .decoder(jwtDecoder))
                                                .authenticationEntryPoint(restAuthenticationEntryPoint)
                                                .accessDeniedHandler(restAccessDeniedHandler))
                                .exceptionHandling(e -> e.authenticationEntryPoint(restAuthenticationEntryPoint)
                                                .accessDeniedHandler(
                                                                restAccessDeniedHandler))
                                .build();
        }
}
