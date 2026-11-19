package com.nep.tsuki.common.constants;

import java.util.Locale;
import java.util.Set;

public final class RegionConstants {
    private RegionConstants() {
    }

    public static final Set<String> ISO_COUNTRY_CODES = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);
}
