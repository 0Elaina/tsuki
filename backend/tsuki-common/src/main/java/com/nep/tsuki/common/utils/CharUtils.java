package com.nep.tsuki.common.utils;

public final class CharUtils {
    private CharUtils() {
    }

    public static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }
}
