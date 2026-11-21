package com.nep.tsuki.common.utils;

import java.net.URI;

import org.apache.commons.validator.routines.UrlValidator;

import com.nep.tsuki.common.api.ApiCode;
import com.nep.tsuki.common.constants.UriConstants;
import com.nep.tsuki.common.exceptions.BusinessException;

public final class UriUtils {
    private UriUtils() {
    }

    private static final UrlValidator HTTP_URL_VALIDATOR = new UrlValidator(
            new String[] { UriConstants.HTTP_LOWER, UriConstants.HTTPS_LOWER });

    public static String normalizeAndValidateHttpUrl(String rawUrl, int maxLength) {
        String normalized = StrUtils.stripToNull(rawUrl);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > maxLength || !HTTP_URL_VALIDATOR.isValid(normalized)) {
            throw new BusinessException(ApiCode.INVALID_URL);
        }
        try {
            URI uri = URI.create(normalized);
            if (uri.getRawUserInfo() != null) {
                throw new BusinessException(ApiCode.INVALID_URL);
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiCode.INVALID_URL);
        }
        return normalized;
    }
}
