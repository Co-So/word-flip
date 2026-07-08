package com.wordflip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 每日学习日志，对应 study_logs 表；热力图与连续打卡数据源。
 */
@Entity
@Table(name = "study_logs")
@IdClass(StudyLogId.class)
@Getter
@Setter
public class StudyLog {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "study_duration_sec", nullable = false, columnDefinition = "INT UNSIGNED")
    private int studyDurationSec = 0;

    @Column(name = "words_viewed", nullable = false, columnDefinition = "INT UNSIGNED")
    private int wordsViewed = 0;

    @Column(name = "quiz_answered", nullable = false, columnDefinition = "INT UNSIGNED")
    private int quizAnswered = 0;

    @Column(name = "quiz_correct", nullable = false, columnDefinition = "INT UNSIGNED")
    private int quizCorrect = 0;

    @Column(name = "activity_score", nullable = false, columnDefinition = "INT UNSIGNED")
    private int activityScore = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** 按 database-design §10.1 重算活跃度分 */
    public void recalculateActivityScore() {
        this.activityScore = wordsViewed + quizAnswered * 2 + quizCorrect;
    }
}
