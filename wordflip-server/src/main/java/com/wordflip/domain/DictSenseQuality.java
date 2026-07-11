package com.wordflip.domain;

/**
 * 义项清洗质量；仅 ok 的 primary 可入测验池（REQ-LEX-4）。
 */
public enum DictSenseQuality {
    ok,
    uncertain,
    reject
}
