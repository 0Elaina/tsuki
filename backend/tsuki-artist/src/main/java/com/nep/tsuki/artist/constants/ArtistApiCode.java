package com.nep.tsuki.artist.constants;

import org.springframework.http.HttpStatus;

import com.nep.tsuki.common.api.ApiCode;

public final class ArtistApiCode {
        private ArtistApiCode() {
        }

        public static final ApiCode DUPLICATE_ARTIST_ALIAS = new ApiCode(HttpStatus.BAD_REQUEST,
                        "DUPLICATE_ARTIST_ALIAS",
                        "艺人别名不能重复");

        public static final ApiCode INVALID_ARTIST_DEBUT_YEAR = new ApiCode(HttpStatus.BAD_REQUEST,
                        "INVALID_ARTIST_DEBUT_YEAR", "艺人出道年份无效");

}
