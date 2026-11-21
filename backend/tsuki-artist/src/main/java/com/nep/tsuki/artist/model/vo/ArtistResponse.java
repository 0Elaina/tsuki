package com.nep.tsuki.artist.model.vo;

import java.time.OffsetDateTime;
import java.util.List;

import com.nep.tsuki.artist.model.entity.Artist;
import com.nep.tsuki.artist.model.entity.ArtistProfile;

import lombok.Builder;

@Builder
public record ArtistResponse(
        String id,
        String name,
        String sortName,
        String type,
        String countryCode,
        Integer debutYear,
        String officialWebsiteUrl,
        String avatarAssetId,
        String avatarUrl,
        String biography,
        List<String> aliases,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ArtistResponse of(Artist artist, ArtistProfile profile, List<String> aliases, String fullAvatarUrl) {
        return ArtistResponse.builder()
                .id(artist.getId().toString())
                .name(artist.getName())
                .sortName(artist.getSortName())
                .type(artist.getType())
                .countryCode(profile == null ? null : profile.getCountryCode())
                .debutYear(profile == null ? null : profile.getDebutYear())
                .officialWebsiteUrl(profile == null ? null : profile.getOfficialWebsiteUrl())
                .avatarAssetId(artist.getAvatarAssetId() == null ? null : artist.getAvatarAssetId().toString())
                .avatarUrl(fullAvatarUrl == null ? null : fullAvatarUrl)
                .biography(profile == null ? null : profile.getBiography())
                .aliases(aliases)
                .createdAt(artist.getCreatedAt())
                .updatedAt(artist.getUpdatedAt())
                .build();
    }
}
