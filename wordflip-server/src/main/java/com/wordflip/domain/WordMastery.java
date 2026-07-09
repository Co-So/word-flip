package com.wordflip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 单词掌握度实体，对应 word_mastery 表（仅测验写入；含稳定性 S 与短窗字段）。
 */
@Entity
@Table(name = "word_mastery")
@Getter
@Setter
public class WordMastery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "word_key", nullable = false, length = 191)
    private String wordKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('unlearned', 'fuzzy', 'unknown')")
    private MasteryLevel level = MasteryLevel.unlearned;

    @Column(name = "has_quiz_history", nullable = false)
    private boolean hasQuizHistory = false;

    @Column(name = "first_quiz_at")
    private Instant firstQuizAt;

    /** 稳定性权值 S（0–100），组详情热力来源 */
    @Column(name = "stability", nullable = false, precision = 6, scale = 2)
    private BigDecimal stability = BigDecimal.ZERO.setScale(2);

    /** 当前短窗内答对已计入升幅 */
    @Column(name = "window_correct_gain", nullable = false, precision = 6, scale = 2)
    private BigDecimal windowCorrectGain = BigDecimal.ZERO.setScale(2);

    @Column(name = "window_started_at")
    private Instant windowStartedAt;

    @Column(name = "recent_wrong_count", nullable = false)
    private int recentWrongCount = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
