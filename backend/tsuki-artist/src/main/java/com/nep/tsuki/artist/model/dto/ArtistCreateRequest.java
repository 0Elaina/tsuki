package com.nep.tsuki.artist.model.dto;

import java.util.List;

import com.nep.tsuki.artist.model.enums.ArtistType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ArtistCreateRequest(
        @NotBlank(message = "艺人名称不能为空") @Size(max = 128, message = "艺人名称不能超过 128 个字符") String name,
        @Size(max = 128, message = "排序名不能超过 128 个字符") String sortName,
        @NotNull(message = "艺人类型不能为空") ArtistType type,
        String countryCode,
        @Size(max = 128, message = "区分说明不能超过 128 个字符") String disambiguation,
        @Size(max = 4000, message = "简介不能超过 4000 个字符") String biography,
        @Size(max = 20, message = "别名最多不能超过 20 个") List<@NotBlank(message = "艺人别名不能为空") @Size(max = 128, message = "艺人别名不能超过 128 个字符") String> aliases) {
}
