package com.nep.tsuki.user.model.vo;

import com.nep.tsuki.user.model.entity.UserAccount;
import com.nep.tsuki.user.model.enums.AccountRole;
import com.nep.tsuki.user.model.enums.AccountStatus;

import lombok.Builder;

@Builder
public record AccountIdentityResponse(
        String id,
        String username,
        String nickname,
        AccountRole role,
        AccountStatus status) {

    public static AccountIdentityResponse of(UserAccount userAccount) {
        return builder()
                .id(userAccount.getId().toString())
                .username(userAccount.getUsername())
                .nickname(userAccount.getNickname())
                .role(AccountRole.valueOf(userAccount.getRole()))
                .status(AccountStatus.valueOf(userAccount.getStatus()))
                .build();
    }
}
