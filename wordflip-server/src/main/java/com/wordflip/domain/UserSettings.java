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
 * 用户偏好与分组大小，1:1 关联 users（user_settings 表）。
 */
@Entity
@Table(name = "user_settings")
@Getter
@Setter
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** 唯一当前学习计划；切换由 LearningPlanService 在事务内更新。 */
    @Column(name = "active_plan_id")
    private Long activePlanId;

    /** 自动分组大小，仅影响尚未入组的新增卡片。 */
    @Column(name = "group_size", nullable = false, columnDefinition = "INT UNSIGNED")
    private int groupSize = 20;

    /** 自动分组策略。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "group_strategy", nullable = false, length = 24)
    private GroupStrategy groupStrategy = GroupStrategy.book_order;

    @Column(name = "auto_speak", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean autoSpeak = true;

    /** 主题模式。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, length = 16)
    private ThemeMode themeMode = ThemeMode.system;

    @Column(name = "study_guide_completed", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean studyGuideCompleted = false;

    @Column(name = "reminder_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean reminderEnabled = false;

    @Column(name = "review_reminder_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean reviewReminderEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "heat_display_mode", nullable = false, length = 16)
    private HeatDisplayMode heatDisplayMode = HeatDisplayMode.combined;

    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_launch_mode", nullable = false, length = 24)
    private QuizLaunchMode quizLaunchMode = QuizLaunchMode.mixed;

    @Column(name = "default_question_limit", nullable = false, columnDefinition = "INT UNSIGNED")
    private int defaultQuestionLimit = 10;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
