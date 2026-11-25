package com.nep.tsuki.media.storage;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;

import com.nep.tsuki.media.constants.MediaStorageConstants;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ImageContentType {

    JPEG(MediaType.IMAGE_JPEG_VALUE),
    PNG(MediaType.IMAGE_PNG_VALUE),
    WEBP(MediaStorageConstants.IMAGE_WEBP_VALUE);

    private final String mimeType;

    public final static Set<String> SUPPORTED_MIME_TYPES = Arrays
            .stream(values())
            .map(ImageContentType::getMimeType)
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isSupported(String contentType) {
        return contentType != null && SUPPORTED_MIME_TYPES.contains(contentType);
    }
}
