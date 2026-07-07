package com.wordflip.util;

/**
 * wordKey 归一化工具：en.trim().toLowerCase()。
 */
public final class WordKeyUtil {

    private WordKeyUtil() {
    }

    public static String normalize(String en) {
        if (en == null) {
            return "";
        }
        return en.trim().toLowerCase();
    }
}
