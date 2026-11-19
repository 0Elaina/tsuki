package com.nep.tsuki.artist.model.vo;

import java.time.OffsetDateTime;
import java.util.List;

import com.nep.tsuki.artist.model.entity.Artist;

import lombok.Builder;

@Builder
public record ArtistResponse(
        String id,
        String name,
        String sortName,
        String type,
        String countryCode,
        String disambiguation,
        String biography,
        List<String> aliases,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ArtistResponse of(Artist artist, List<String> aliases) {
        return ArtistResponse.builder()
                .id(artist.getId().toString())
                .name(artist.getName())
                .sortName(artist.getSortName())
                .type(artist.getType())
                .countryCode(artist.getCountryCode())
                .disambiguation(artist.getDisambiguation())
                .biography(artist.getBiography())
                .aliases(aliases)
                .createdAt(artist.getCreatedAt())
                .updatedAt(artist.getUpdatedAt())
                .build();
    }
}
