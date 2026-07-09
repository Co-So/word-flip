package com.wordflip.util;

/**
 * 确定性字符串哈希工具（P3-B06 / REQ-STAIN-1）。
 * <p>
 * 算法与 Android {@code StainGenerator.stableSeed} 一致：多项式滚动哈希 {@code 31 * hash + codePoint}。
 * <p>
 * <b>默认污渍 seed 约定：</b>{@code stableHash(String.valueOf(userId) + wordKey)}，
 * 即直接拼接用户 ID 与 wordKey（无分隔符），与 api-modules §2.4 / database-design 一致。
 * 无 {@code word_stains} 行时仅用该 seed 返回内存配置，不落库，直至 regenerate / batch / replace / 隐藏态变更。
 */
public final class StableHash {

    private StableHash() {
    }

    /**
     * 对输入字符串计算确定性 long 哈希。
     *
     * @param input 非 null 字符串；null 按空串处理
     * @return 稳定可复现的 long 值
     */
    public static long stableHash(String input) {
        String s = input == null ? "" : input;
        long hash = 0L;
        for (int i = 0; i < s.length(); i++) {
            hash = 31L * hash + s.charAt(i);
        }
        return hash;
    }

    /**
     * 无自定义污渍行时的默认 seed：{@code userId} 十进制字符串直接拼接 {@code wordKey}。
     */
    public static long defaultStainSeed(long userId, String wordKey) {
        return stableHash(String.valueOf(userId) + (wordKey == null ? "" : wordKey));
    }
}
