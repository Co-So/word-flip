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

import java.time.Instant;

/**
 * 单词掌握度实体，对应 word_mastery 表（仅测验写入；Groups 读 API 只读聚合）。
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

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
