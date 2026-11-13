package com.nep.tsuki.auth.model.vo;

public record CsrfTokenResponse(
        String token,
        String headerName) {

}
