package com.wordflip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * SRS 复习计划实体，对应 review_plans 表；与 word_mastery 1:1 (user_id, word_key)。
 */
@Entity
@Table(name = "review_plans")
@Getter
@Setter
public class ReviewPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "word_key", nullable = false, length = 191)
    private String wordKey;

    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private int stage = 0;

    @Column(name = "next_review_at")
    private LocalDate nextReviewAt;

    @Column(name = "last_quiz_at")
    private Instant lastQuizAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
