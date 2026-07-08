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

/**
 * 测验作答明细，对应 quiz_answers 表；连续答错判定依赖 idx_qa_user_word_time。
 */
@Entity
@Table(name = "quiz_answers")
@Getter
@Setter
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false, columnDefinition = "CHAR(36)")
    private String sessionId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "word_key", nullable = false, length = 191)
    private String wordKey;

    @Column(name = "question_index", nullable = false)
    private int questionIndex;

    @Column(name = "user_answer", nullable = false, length = 512)
    private String userAnswer;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "is_consecutive_wrong", nullable = false)
    private boolean consecutiveWrong;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt = Instant.now();
}
