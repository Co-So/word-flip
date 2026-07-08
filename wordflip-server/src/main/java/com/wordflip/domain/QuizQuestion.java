package com.wordflip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话题面快照，对应 quiz_questions 表；创建 session 时写入标准答案供判题。
 */
@Entity
@Table(name = "quiz_questions")
@Getter
@Setter
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, columnDefinition = "CHAR(36)")
    private String sessionId;

    @Column(name = "question_index", nullable = false)
    private int questionIndex;

    @Column(name = "word_key", nullable = false, length = 191)
    private String wordKey;

    @Column(name = "expected_en", nullable = false, length = 191)
    private String expectedEn;

    @Column(name = "prompt_cn", nullable = false, length = 512)
    private String promptCn;

    @Column(name = "prompt_pos", length = 32)
    private String promptPos;

    @Column(name = "prompt_ph", length = 64)
    private String promptPh;
}
