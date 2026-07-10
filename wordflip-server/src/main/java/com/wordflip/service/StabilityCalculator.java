package com.wordflip.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * 稳定性权值 S 纯函数（REQ-EBBING-8 / api-modules §2.2）。
 * <p>
 * 答对前估计可提取性 R=exp(-gapDays/S_days)，ΔS∝(1−R)；24h 窗答对累计升幅封顶。
 */
public final class StabilityCalculator {

    public static final double WEIGHT_MIN = 0.0;
    public static final double WEIGHT_MAX = 100.0;
    public static final long WINDOW_HOURS = 24;
    public static final double CAP_CORRECT_IN_WINDOW = 1.0;
    public static final double GAIN_MAX = 4.0;
    public static final double DELTA_CORRECT_MIN = 0.05;
    public static final double DELTA_CORRECT_MAX = 3.0;
    public static final double DELTA_WRONG_CAP = 8.0;
    /**
     * 该 skill 首次答对的稳定性下限（heatLevel≥1「初识」）。
     * 无测验史时 gapDays=0→R=1，公式仅涨 0.05，热力会长期停在「新词」。
     */
    public static final double INITIAL_CORRECT_STABILITY = 12.0;

    private StabilityCalculator() {
    }

    /** S → 稳定天数（用于遗忘曲线） */
    public static double stabilityDays(double stability) {
        return 0.5 + (clamp(stability, WEIGHT_MIN, WEIGHT_MAX) / 100.0) * 59.5;
    }

    /** 距上次测验的天数（小数）；无历史视为 0 */
    public static double gapDays(Instant lastQuizAt, Instant now) {
        if (lastQuizAt == null) {
            return 0.0;
        }
        long seconds = Math.max(0, Duration.between(lastQuizAt, now).getSeconds());
        return seconds / 86_400.0;
    }

    /** 复习前可提取性 R */
    public static double retrievability(double gapDays, double stability) {
        double sDays = stabilityDays(stability);
        return Math.exp(-gapDays / sDays);
    }

    public static boolean isWindowExpired(Instant windowStartedAt, Instant now) {
        if (windowStartedAt == null) {
            return true;
        }
        return Duration.between(windowStartedAt, now).toHours() >= WINDOW_HOURS;
    }

    /**
     * 答对升幅（已应用短窗剩余额度）。
     *
     * @return 实际计入的 ΔS（可为 0）
     */
    public static double correctDelta(double stability, double gapDays, double windowCorrectGain) {
        double r = retrievability(gapDays, stability);
        double raw = clamp(GAIN_MAX * (1.0 - r), DELTA_CORRECT_MIN, DELTA_CORRECT_MAX);
        double remain = CAP_CORRECT_IN_WINDOW - windowCorrectGain;
        if (remain <= 0) {
            return 0.0;
        }
        return Math.min(raw, remain);
    }

    /**
     * 答错降幅（正数）。
     *
     * @param recentWrongCountAfter 计入本次后的窗内答错次数（≥1）
     */
    public static double wrongDelta(double stability, double gapDays, int recentWrongCountAfter) {
        double r = retrievability(gapDays, stability);
        double delta = 1.5 + 2.0 * r;
        if (recentWrongCountAfter == 2) {
            delta *= 1.5;
        } else if (recentWrongCountAfter >= 3) {
            delta *= 2.0;
        }
        return Math.min(delta, DELTA_WRONG_CAP);
    }

    public static double applyDelta(double stability, double signedDelta) {
        return clamp(stability + signedDelta, WEIGHT_MIN, WEIGHT_MAX);
    }

    /** heatLevel 0–4 */
    public static int heatLevel(double stability) {
        double s = clamp(stability, WEIGHT_MIN, WEIGHT_MAX);
        if (s < 10.0) {
            return 0;
        }
        if (s < 30.0) {
            return 1;
        }
        if (s < 55.0) {
            return 2;
        }
        if (s < 80.0) {
            return 3;
        }
        return 4;
    }

    public static BigDecimal toStored(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static double fromStored(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
