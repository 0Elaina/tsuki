package com.nep.tsuki.auth.service.impl;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.nep.tsuki.auth.config.JwtProperties;
import com.nep.tsuki.auth.constants.JwtConstants;
import com.nep.tsuki.auth.model.dto.IssuedTokens;
import com.nep.tsuki.auth.model.enums.AccountType;
import com.nep.tsuki.auth.model.enums.TokenType;
import com.nep.tsuki.auth.service.TokenService;
import com.nep.tsuki.utils.TimeUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    /**
     * 签发双令牌
     * 
     * @param accountType      账号类型
     * @param accountId        账号ID
     * @param sid              会话ID
     * @param refreshJti       刷新令牌ID
     * @param refreshExpiresAt 刷新令牌过期时间
     * @param now              当前时间
     * @return 签发的令牌
     */
    @Override
    public IssuedTokens issue(AccountType accountType, Long accountId, String sid, String refreshJti,
            Instant refreshExpiresAt, Instant now) {
        Duration accessTtl = jwtProperties.accessTtl();
        Instant accessExpiresAt = TimeUtils.minInstant(now.plus(accessTtl), refreshExpiresAt);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        // 构造访问令牌
        JwtClaimsSet accessClaims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(String.valueOf(accountId))
                .issuedAt(now)
                .expiresAt(accessExpiresAt)
                .claim(JwtConstants.ACCOUNT_TYPE, accountType.name())
                .claim(JwtConstants.TOKEN_TYPE, TokenType.ACCESS.name())
                .build();

        // 构造刷新令牌
        JwtClaimsSet refreshClaims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(String.valueOf(accountId))
                .issuedAt(now)
                .expiresAt(refreshExpiresAt)
                .id(refreshJti)
                .claim(JwtConstants.ACCOUNT_TYPE, accountType.name())
                .claim(JwtConstants.TOKEN_TYPE, TokenType.REFRESH.name())
                .claim(JwtConstants.SID, sid)
                .build();

        long expiresIn = Duration.between(now, accessExpiresAt).getSeconds();

        return new IssuedTokens(
                jwtEncoder.encode(JwtEncoderParameters.from(header, accessClaims)).getTokenValue(),
                jwtEncoder.encode(JwtEncoderParameters.from(header, refreshClaims)).getTokenValue(),
                expiresIn);
    }
}
