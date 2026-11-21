package com.nep.tsuki.media.image.processing;

import lombok.Builder;

@Builder
public record ProcessedImage(
                byte[] bytes,
                String contentType,
                String extension,
                int width,
                int height) {

}
