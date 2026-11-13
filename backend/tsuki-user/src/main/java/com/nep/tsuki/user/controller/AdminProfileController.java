package com.nep.tsuki.user.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nep.tsuki.api.ApiResponse;
import com.nep.tsuki.user.model.vo.AccountIdentityResponse;
import com.nep.tsuki.user.service.AccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "管理员资料接口")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminProfileController {
    private final AccountService accountService;

    /**
     * 获取当前登录管理员信息
     * 
     * @param jwt Jwt
     * @return 管理员身份信息
     */
    @Operation(summary = "获取当前登录管理员信息")
    @GetMapping("/me")
    public ApiResponse<AccountIdentityResponse> me(
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(accountService.findById(Long.valueOf(jwt.getSubject())));
    }
}
