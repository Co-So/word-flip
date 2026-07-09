package com.wordflip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 用户最近学习的分组，今日页最多展示 3 条。
 */
@Entity
@Table(name = "user_recent_groups")
@IdClass(UserRecentGroupId.class)
@Getter
@Setter
public class UserRecentGroup {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "last_studied_at", nullable = false)
    private Instant lastStudiedAt = Instant.now();
}
