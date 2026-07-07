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
 * 学习分组实体，对应 groups 表（auto 增量追加 / custom 手动）。
 */
@Entity
@Table(name = "`groups`")
@Getter
@Setter
public class StudyGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String name;

    /** 对齐 Flyway groups.source ENUM('auto','custom') */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('auto', 'custom')")
    private GroupSource source;

    /** 对齐 Flyway groups.status ENUM('not_started','learning','completed') */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('not_started', 'learning', 'completed')")
    private GroupStatus status = GroupStatus.not_started;

    @Column(name = "sort_order", nullable = false, columnDefinition = "INT UNSIGNED")
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
