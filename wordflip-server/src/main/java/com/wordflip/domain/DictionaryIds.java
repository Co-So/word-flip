package com.wordflip.domain;

/**
 * 内置词典 ID 常量（与 dictionaries.id / Flyway 种子一致）。
 */
public final class DictionaryIds {

    public static final String CURATED = "wordflip_curated";
    public static final String WIKTIONARY = "wiktionary_zh";
    public static final String CONCISE = "wordflip_concise";
    public static final String WORDNET = "wordnet";

    private DictionaryIds() {
    }
}
