package com.nep.tsuki.file.model.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum FileKind {
    IMAGE("image");

    private final String pathSegment;
}
