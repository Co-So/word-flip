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

    /** 对齐 Flyway user_settings.group_size TINYINT UNSIGNED */
    @Column(name = "group_size", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private int groupSize = 20;

    /** 对齐 Flyway user_settings.group_strategy ENUM（REQ-BOOK-22~24） */
    @Enumerated(EnumType.STRING)
    @Column(name = "group_strategy", nullable = false, columnDefinition = "ENUM('book_order', 'frequency', 'random')")
    private GroupStrategy groupStrategy = GroupStrategy.book_order;

    @Column(name = "auto_speak", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean autoSpeak = true;

    /** 对齐 Flyway user_settings.theme_mode ENUM('system','light','dark') */
    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, columnDefinition = "ENUM('system', 'light', 'dark')")
    private ThemeMode themeMode = ThemeMode.system;

    @Column(name = "study_guide_completed", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean studyGuideCompleted = false;

    @Column(name = "reminder_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean reminderEnabled = false;

    @Column(name = "review_reminder_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean reviewReminderEnabled = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
