package com.nep.tsuki.auth.controller;

import org.springframework.core.env.Environment;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nep.tsuki.api.ApiResponse;
import com.nep.tsuki.auth.config.JwtProperties;
import com.nep.tsuki.auth.constants.AuthApiCode;
import com.nep.tsuki.auth.constants.CookieConstants;
import com.nep.tsuki.auth.model.dto.IssuedTokens;
import com.nep.tsuki.auth.model.dto.LoginRequest;
import com.nep.tsuki.auth.model.enums.AccountType;
import com.nep.tsuki.auth.model.vo.CsrfTokenResponse;
import com.nep.tsuki.auth.model.vo.LoginResponse;
import com.nep.tsuki.auth.service.AuthService;
import com.nep.tsuki.auth.utils.CookieUtils;
import com.nep.tsuki.exceptions.BusinessException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "用户认证接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserAuthController {
    private final AuthService authService;
    private final CookieCsrfTokenRepository cookieCsrfTokenRepository;
    private final JwtProperties jwtProperties;
    private final Environment environment;

    /**
     * 获取 CSRF 令牌
     * 
     * @param csrfToken CSRF 令牌
     */
    @Operation(summary = "获取 CSRF Token")
    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> getCsrfToken(CsrfToken csrfToken) {
        return ApiResponse.success(new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName()));
    }

    /**
     * 用户登录
     * 
     * @param loginRequest 登录请求
     * @param request      请求
     * @param response     响应
     * @return 登录响应
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        IssuedTokens issuedTokens = authService.login(loginRequest.username(), loginRequest.password(),
                request.getRemoteAddr(), AccountType.USER);
        CookieUtils.setCookie(response, CookieConstants.USER_RT_COOKIE_NAME, issuedTokens.refreshToken(),
                CookieConstants.USER_RT_COOKIE_PATH,
                jwtProperties.refreshUserIdleTtl(), environment);
        cookieCsrfTokenRepository.saveToken(null, request, response);
        return ApiResponse.success(new LoginResponse(issuedTokens.accessToken(), issuedTokens.expiresIn()));
    }

    /**
     * 刷新访问令牌
     * 
     * @param refreshToken 刷新令牌
     * @param response     响应
     * @return 登录响应
     */
    @Operation(summary = "刷新访问令牌")
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @CookieValue(name = CookieConstants.USER_RT_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            IssuedTokens issuedTokens = authService.refresh(AccountType.USER, refreshToken);
            CookieUtils.setCookie(response, CookieConstants.USER_RT_COOKIE_NAME, issuedTokens.refreshToken(),
                    CookieConstants.USER_RT_COOKIE_PATH,
                    jwtProperties.refreshUserIdleTtl(), environment);
            return ApiResponse.success(new LoginResponse(issuedTokens.accessToken(), issuedTokens.expiresIn()));
        } catch (BusinessException ex) {
            if (ex.getApiCode() == AuthApiCode.INVALID_REFRESH_TOKEN) {
                CookieUtils.clearCookie(response, CookieConstants.USER_RT_COOKIE_NAME,
                        CookieConstants.USER_RT_COOKIE_PATH, environment);
            }
            throw ex;
        }

    }

    /**
     * 用户登出
     * 
     * @param refreshToken 刷新令牌
     * @param request      请求
     * @param response     响应
     */
    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = CookieConstants.USER_RT_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.logout(AccountType.USER, refreshToken);
        CookieUtils.clearCookie(response, CookieConstants.USER_RT_COOKIE_NAME, CookieConstants.USER_RT_COOKIE_PATH,
                environment);
        cookieCsrfTokenRepository.saveToken(null, request, response);
        return ApiResponse.success();
    }
}
