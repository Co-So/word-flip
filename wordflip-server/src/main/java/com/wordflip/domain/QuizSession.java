package com.wordflip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 默写测验会话实体，对应 quiz_sessions 表（REQ-NAV-6：每次进入新建）。
 */
@Entity
@Table(name = "quiz_sessions")
@Getter
@Setter
public class QuizSession {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('today', 'study', 'retry')")
    private QuizSessionSource source = QuizSessionSource.today;

    @Column(name = "group_id")
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('in_progress', 'completed')")
    private QuizSessionStatus status = QuizSessionStatus.in_progress;

    @Column(name = "question_limit", nullable = false)
    private int questionLimit = 10;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "current_index", nullable = false)
    private int currentIndex = 0;

    @Column(nullable = false)
    private int score = 0;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;
}
