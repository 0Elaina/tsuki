package com.nep.tsuki.auth.model.vo;

public record LoginResponse(
        String accessToken,
        long expiresIn) {

}
