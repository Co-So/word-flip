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
import java.time.LocalDate;

/**
 * 单词按题型的学习进度（word_skill_progress）：热力 S + 队列三态 + SRS。
 */
@Entity
@Table(name = "word_skill_progress")
@Getter
@Setter
public class WordSkillProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "word_key", nullable = false, length = 191)
    private String wordKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('dictation', 'choice')")
    private Skill skill = Skill.dictation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('unlearned', 'fuzzy', 'unknown')")
    private MasteryLevel level = MasteryLevel.unlearned;

    @Column(name = "has_quiz_history", nullable = false)
    private boolean hasQuizHistory = false;

    @Column(name = "first_quiz_at")
    private Instant firstQuizAt;

    @Column(name = "stability", nullable = false, precision = 6, scale = 2)
    private BigDecimal stability = BigDecimal.ZERO.setScale(2);

    @Column(name = "window_correct_gain", nullable = false, precision = 6, scale = 2)
    private BigDecimal windowCorrectGain = BigDecimal.ZERO.setScale(2);

    @Column(name = "window_started_at")
    private Instant windowStartedAt;

    /** 对齐 Flyway word_skill_progress.recent_wrong_count INT UNSIGNED */
    @Column(name = "recent_wrong_count", nullable = false, columnDefinition = "INT UNSIGNED")
    private int recentWrongCount = 0;

    /** 对齐 Flyway word_skill_progress.stage TINYINT UNSIGNED（SRS 0..5） */
    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private int stage = 0;

    @Column(name = "next_review_at")
    private LocalDate nextReviewAt;

    @Column(name = "last_quiz_at")
    private Instant lastQuizAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
