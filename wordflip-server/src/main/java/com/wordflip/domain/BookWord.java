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
 * 词书词条；word_key 为 LOWER(TRIM(en)) 归一化键。
 */
@Entity
@Table(name = "book_words")
@Getter
@Setter
public class BookWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "word_key", nullable = false, length = 191)
    private String wordKey;

    @Column(nullable = false, length = 191)
    private String en;

    @Column(nullable = false, length = 512)
    private String cn;

    @Column(length = 32)
    private String pos;

    @Column(length = 64)
    private String ph;

    @Column(name = "detail_json", columnDefinition = "json")
    private String detailJson;

    @Column(name = "sort_order", nullable = false, columnDefinition = "INT UNSIGNED")
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
