package com.nep.tsuki.media.model.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum MediaVariantCode {
    PREVIEW("preview");

    private final String fileStem;
}
