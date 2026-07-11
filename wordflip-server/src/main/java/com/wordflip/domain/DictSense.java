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
 * 词典义项；英汉用 cn，英英用 en_gloss；测验默认 is_primary=1 且 quality=ok。
 */
@Entity
@Table(name = "dict_senses")
@Getter
@Setter
public class DictSense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dict_id", nullable = false, length = 32)
    private String dictId = DictionaryIds.CURATED;

    @Column(name = "word_key", nullable = false, length = 191)
    private String wordKey;

    @Column(length = 32)
    private String pos;

    /** 纯中文释义；WordNet 等英英可空 */
    @Column(length = 512)
    private String cn;

    /** 英英释义（REQ-LEX-10） */
    @Column(name = "en_gloss", length = 512)
    private String enGloss;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "sort_order", nullable = false, columnDefinition = "INT UNSIGNED")
    private int sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DictSenseQuality quality = DictSenseQuality.ok;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
