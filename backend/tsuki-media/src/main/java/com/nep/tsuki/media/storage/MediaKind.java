package com.nep.tsuki.media.storage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum MediaKind {
    IMAGE("image");

    private final String pathSegment;
}
