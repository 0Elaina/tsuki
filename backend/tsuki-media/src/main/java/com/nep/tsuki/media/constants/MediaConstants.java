package com.nep.tsuki.media.constants;

import java.util.List;

import org.springframework.http.MediaType;

public final class MediaConstants {
    private MediaConstants() {
    }

    public static final String IMAGE_WEBP_VALUE = "image/webp";
    public static final List<String> SUPPORTED_IMAGE_TYPES = List.of(MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE, IMAGE_WEBP_VALUE);
}
