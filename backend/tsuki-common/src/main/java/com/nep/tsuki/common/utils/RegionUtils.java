package com.nep.tsuki.common.utils;

import java.util.Locale;

import org.springframework.util.StringUtils;

import com.nep.tsuki.common.api.ApiCode;
import com.nep.tsuki.common.constants.RegionConstants;
import com.nep.tsuki.common.exceptions.BusinessException;

public final class RegionUtils {
    private RegionUtils() {

    }

    /**
     * 校验国家代码是否符合规范，若不符合则抛出业务异常
     * 
     * @param countryCode 国家代码
     * @return 规范化后的国家代码
     */
    public static String getNormalizedCountryCode(String countryCode) {
        if (!StringUtils.hasText(countryCode))
            return null;
        countryCode = countryCode.strip();
        if (countryCode.length() != 2 || !CharUtils.isAsciiLetter(countryCode.charAt(0))
                || !CharUtils.isAsciiLetter(countryCode.charAt(1))) {
            throw new BusinessException(ApiCode.INVALID_COUNTRY_CODE);
        }
        String normalized = countryCode.toUpperCase(Locale.ROOT);
        if (!RegionConstants.ISO_COUNTRY_CODES.contains(normalized)) {
            throw new BusinessException(ApiCode.INVALID_COUNTRY_CODE);
        }
        return normalized;
    }
}
