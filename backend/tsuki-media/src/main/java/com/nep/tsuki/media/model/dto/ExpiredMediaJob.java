package com.nep.tsuki.media.model.dto;

public record ExpiredMediaJob(
                Long id,
                Long assetId,
                String type,
                Integer attemptCount) {

}
