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
 * 词书实体：builtin 全员可见；imported 归属 user_id。
 */
@Entity
@Table(name = "books")
@Getter
@Setter
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对齐 Flyway books.source ENUM('builtin','imported') */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('builtin', 'imported')")
    private BookSource source;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "declared_count", columnDefinition = "INT UNSIGNED")
    private Integer declaredCount;

    @Column(name = "word_count", nullable = false, columnDefinition = "INT UNSIGNED")
    private int wordCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
