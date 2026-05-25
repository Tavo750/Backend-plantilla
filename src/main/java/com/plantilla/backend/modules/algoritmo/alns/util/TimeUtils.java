package com.plantilla.backend.modules.algoritmo.alns.util;

/**
 * Utilidades para conversión y manejo de tiempos en el algoritmo ALNS.
 *
 * Epoch: 2026-01-01 00:00:00 UTC = minuto 0
 * Todos los tiempos se normalizan a minutos absolutos UTC desde este epoch.
 */
public class TimeUtils {

    public static final int EPOCH_YEAR = 2026;
    public static final int EPOCH_MONTH = 1;
    public static final int EPOCH_DAY = 1;

    private static final int[] DAYS_IN_MONTH = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    public static long toAbsoluteUTC(int year, int month, int day, int hour, int minute, int gmtOffset) {
        int dayIndex = daysBetweenEpoch(year, month, day);
        long localMinutes = (long) dayIndex * 1440 + hour * 60 + minute;
        return localMinutes - (long) gmtOffset * 60;
    }

    public static int daysBetweenEpoch(int year, int month, int day) {
        int totalDays = 0;
        for (int y = EPOCH_YEAR; y < year; y++) {
            totalDays += isLeapYear(y) ? 366 : 365;
        }
        for (int m = 1; m < month; m++) {
            totalDays += daysInMonth(year, m);
        }
        totalDays += (day - 1);
        return totalDays;
    }

    public static String dayIndexToDate(int dayIndex) {
        int year = EPOCH_YEAR;
        int remaining = dayIndex;

        while (true) {
            int daysInYear = isLeapYear(year) ? 366 : 365;
            if (remaining < daysInYear) break;
            remaining -= daysInYear;
            year++;
        }

        int month = 1;
        while (true) {
            int dim = daysInMonth(year, month);
            if (remaining < dim) break;
            remaining -= dim;
            month++;
        }

        int day = remaining + 1;
        return String.format("%04d-%02d-%02d", year, month, day);
    }

    public static String minutosUTCToString(long minutosUTC) {
        if (minutosUTC < 0) return "N/A";
        int dayIndex = (int) (minutosUTC / 1440);
        int minuteOfDay = (int) (minutosUTC % 1440);
        int hour = minuteOfDay / 60;
        int minute = minuteOfDay % 60;
        return dayIndexToDate(dayIndex) + " " + String.format("%02d:%02d", hour, minute) + " UTC";
    }

    public static int parseDateToDayIndex(String yyyymmdd) {
        int year = Integer.parseInt(yyyymmdd.substring(0, 4));
        int month = Integer.parseInt(yyyymmdd.substring(4, 6));
        int day = Integer.parseInt(yyyymmdd.substring(6, 8));
        return daysBetweenEpoch(year, month, day);
    }

    public static int parseTimeToMinutes(String hhmm) {
        String[] parts = hhmm.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return hour * 60 + minute;
    }

    private static int daysInMonth(int year, int month) {
        if (month == 2 && isLeapYear(year)) return 29;
        return DAYS_IN_MONTH[month];
    }

    private static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }
}
