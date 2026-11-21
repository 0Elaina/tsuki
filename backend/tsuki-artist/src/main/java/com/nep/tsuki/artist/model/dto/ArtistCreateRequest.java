package com.nep.tsuki.artist.model.dto;

import java.util.List;

import com.nep.tsuki.artist.constants.ArtistConstants;
import com.nep.tsuki.artist.model.enums.ArtistType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ArtistCreateRequest(
        @NotBlank(message = "艺人名称不能为空") @Size(max = 128, message = "艺人名称不能超过 128 个字符") String name,

        @Size(max = 128, message = "排序名不能超过 128 个字符") String sortName,

        @NotNull(message = "艺人类型不能为空") ArtistType type,

        @Size(max = 16, message = "国家/地区代码长度不合法") String countryCode,

        @Min(value = 1, message = "出道年份不能小于 1") Integer debutYear,

        @Size(max = ArtistConstants.MAX_WEBSITE_URL_LENGTH, message = "官网链接过长") String officialWebsiteUrl,

        @Positive(message = "头像资产 ID 必须为正整数") Long avatarAssetId,

        @Size(max = 4000, message = "简介不能超过 4000 个字符") String biography,

        @Size(max = 20, message = "别名最多不能超过 20 个") List<@NotBlank(message = "艺人别名不能为空") @Size(max = 128, message = "艺人别名不能超过 128 个字符") String> aliases) {
}