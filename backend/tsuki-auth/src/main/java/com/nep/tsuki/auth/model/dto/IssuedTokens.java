package com.nep.tsuki.auth.model.dto;

public record IssuedTokens(
        String accessToken,
        String refreshToken,
        long expiresIn) {

}
