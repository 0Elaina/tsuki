package com.nep.tsuki.common.utils;

import java.net.URI;
import java.util.Objects;

import org.springframework.util.StringUtils;

public final class UriUtils {
    private UriUtils() {
    }

    /**
     * 判断 URI 是否为纯净的 HTTP(S) 根地址
     */
    public static boolean isHttpRootUri(URI uri) {
        if (uri == null) {
            return false;
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }

        // 必须有 host，且不能包含 userInfo / query / fragment
        if (!StringUtils.hasText(uri.getHost())
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            return false;
        }

        String rawPath = uri.getRawPath();
        return rawPath == null || rawPath.isEmpty() || "/".equals(rawPath);
    }

    /**
     * 规范化 HTTP(S) 根地址：移除末尾多余的 "/"，保证 IPv4、域名、IPv6 字面量均安全
     *
     * @throws NullPointerException     当 uri 为 null 时
     * @throws IllegalArgumentException 当 uri 不是合法的 HTTP(S) 根地址时
     */
    public static URI normalizeHttpRootUri(URI uri) {
        Objects.requireNonNull(uri, "URI 不能为空");
        if (!isHttpRootUri(uri)) {
            throw new IllegalArgumentException(
                    "URI 必须是含主机、无凭据、无查询参数和片段的 HTTP(S) 根地址: " + uri);
        }

        if ("/".equals(uri.getRawPath())) {
            String text = uri.toASCIIString();
            return URI.create(text.substring(0, text.length() - 1));
        }
        return uri;
    }
}
