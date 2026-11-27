package com.nep.tsuki.media.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import com.nep.tsuki.media.constants.MediaStorageConstants;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ImageContentType {

    JPEG(MediaType.IMAGE_JPEG_VALUE, "jpg", List.of("jpg", "jpeg")),
    PNG(MediaType.IMAGE_PNG_VALUE, "png", List.of("png")),
    WEBP(MediaStorageConstants.IMAGE_WEBP_VALUE, "webp", List.of("webp"));

    private final String mimeType;
    private final String extension;
    private final List<String> formatNames;

    public static final Set<String> SUPPORTED_MIME_TYPES = Arrays
            .stream(values())
            .map(ImageContentType::getMimeType)
            .collect(Collectors.toUnmodifiableSet());

    public static final Map<String, ImageContentType> MIME_TYPE_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ImageContentType::getMimeType, Function.identity()));

    public static final Map<String, ImageContentType> FORMAT_NAME_MAP = Arrays.stream(values())
            .flatMap(type -> type.formatNames.stream().map(name -> Map.entry(name, type)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    public static boolean isSupported(String contentType) {
        return contentType != null && SUPPORTED_MIME_TYPES.contains(contentType);
    }

    public static ImageContentType fromMimeType(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return null;
        }
        return MIME_TYPE_MAP.get(mimeType.strip().toLowerCase(Locale.ROOT));
    }

    public static ImageContentType fromFormatName(String formatName) {
        if (!StringUtils.hasText(formatName)) {
            return null;
        }
        return FORMAT_NAME_MAP.get(formatName.strip().toLowerCase(Locale.ROOT));
    }
}
