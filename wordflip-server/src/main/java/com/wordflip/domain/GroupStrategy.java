package com.wordflip.domain;

/**
 * 自动分组策略（REQ-BOOK-22~24；对齐 openapi GroupStrategy）。
 */
public enum GroupStrategy {
    /** 按词书勾选顺序合并，书内按 sort_order */
    book_order,
    /** 按 word_freq_ranks 全局 rank 升序；无 rank 词回退 book_order 相对序 */
    frequency,
    /** 基于 userId + bookIds 的稳定随机打乱 */
    random
}
