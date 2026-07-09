package com.wordflip.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StableHash 确定性单测（P3-B06）。
 */
class StableHashTest {

    @Test
    void stableHash_isDeterministic() {
        long a = StableHash.stableHash("1apple");
        long b = StableHash.stableHash("1apple");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void defaultStainSeed_usesUserIdPlusWordKeyConcatenation() {
        // 默认 seed = stableHash(userId + wordKey)，无分隔符
        long expected = StableHash.stableHash("42apple");
        assertThat(StableHash.defaultStainSeed(42L, "apple")).isEqualTo(expected);
    }

    @Test
    void stableHash_matchesPolynomialRollingHash() {
        String input = "7hello";
        long expected = 0L;
        for (int i = 0; i < input.length(); i++) {
            expected = 31L * expected + input.charAt(i);
        }
        assertThat(StableHash.stableHash(input)).isEqualTo(expected);
    }
}
