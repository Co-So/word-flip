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
 * 测验作答明细；连续答错按同 skill 判定。
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('dictation', 'choice')")
    private Skill skill = Skill.dictation;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false,
            columnDefinition = "ENUM('dictation', 'choice_en_cn', 'choice_cn_en')")
    private QuestionType questionType = QuestionType.dictation;

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
