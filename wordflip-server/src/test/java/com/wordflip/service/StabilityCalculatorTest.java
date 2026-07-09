package com.wordflip.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * StabilityCalculator 纯函数单测。
 */
class StabilityCalculatorTest {

    @Test
    void heatLevel_mapsBands() {
        assertThat(StabilityCalculator.heatLevel(0)).isEqualTo(0);
        assertThat(StabilityCalculator.heatLevel(9.9)).isEqualTo(0);
        assertThat(StabilityCalculator.heatLevel(10)).isEqualTo(1);
        assertThat(StabilityCalculator.heatLevel(30)).isEqualTo(2);
        assertThat(StabilityCalculator.heatLevel(55)).isEqualTo(3);
        assertThat(StabilityCalculator.heatLevel(80)).isEqualTo(4);
        assertThat(StabilityCalculator.heatLevel(100)).isEqualTo(4);
    }

    @Test
    void correctDelta_longGapGreaterThanShortGap() {
        double shortDelta = StabilityCalculator.correctDelta(40, 0.1, 0);
        double longDelta = StabilityCalculator.correctDelta(40, 10, 0);
        assertThat(longDelta).isGreaterThan(shortDelta);
    }

    @Test
    void correctDelta_respectsWindowCap() {
        double delta = StabilityCalculator.correctDelta(40, 10, 0.95);
        assertThat(delta).isCloseTo(0.05, within(0.001));
        assertThat(StabilityCalculator.correctDelta(40, 10, 1.0)).isEqualTo(0.0);
    }

    @Test
    void wrongDelta_escalatesWithCount() {
        double first = StabilityCalculator.wrongDelta(50, 1, 1);
        double second = StabilityCalculator.wrongDelta(50, 1, 2);
        double third = StabilityCalculator.wrongDelta(50, 1, 3);
        assertThat(second).isGreaterThan(first);
        assertThat(third).isGreaterThan(second);
        assertThat(third).isLessThanOrEqualTo(StabilityCalculator.DELTA_WRONG_CAP);
    }
}
