package com.nep.tsuki.user.model.vo;

import com.nep.tsuki.user.model.entity.UserAccount;

import lombok.Builder;

@Builder
public record AccountIdentityResponse(
        String id,
        String username,
        String nickname,
        String role) {

    public static AccountIdentityResponse of(UserAccount userAccount) {
        return builder()
                .id(userAccount.getId().toString())
                .username(userAccount.getUsername())
                .nickname(userAccount.getNickname())
                .role(userAccount.getRole())
                .build();
    }
}
