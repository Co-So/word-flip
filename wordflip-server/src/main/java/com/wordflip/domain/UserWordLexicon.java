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
 * 用户学习域词典：测验判题与卡片展示的 cn/en 真相来源。
 */
@Entity
@Table(name = "user_word_lexicon")
@Getter
@Setter
public class UserWordLexicon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

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

    @Column(name = "source_book_id")
    private Long sourceBookId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
