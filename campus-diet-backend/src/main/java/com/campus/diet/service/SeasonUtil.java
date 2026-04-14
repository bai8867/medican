package com.campus.diet.service;

import java.time.LocalDate;

public final class SeasonUtil {

    private SeasonUtil() {
    }

    public static String currentSeasonCode(LocalDate d) {
        int m = d.getMonthValue();
        if (m >= 3 && m <= 5) {
            return "spring";
        }
        if (m >= 6 && m <= 8) {
            return "summer";
        }
        if (m >= 9 && m <= 11) {
            return "autumn";
        }
        return "winter";
    }
}
