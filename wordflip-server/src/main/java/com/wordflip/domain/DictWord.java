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
 * 词典词头；主键 (dict_id, word_key)。
 */
@Entity
@Table(name = "dict_words")
@IdClass(DictWordId.class)
@Getter
@Setter
public class DictWord {

    @Id
    @Column(name = "dict_id", length = 32)
    private String dictId = DictionaryIds.CURATED;

    @Id
    @Column(name = "word_key", length = 191)
    private String wordKey;

    @Column(nullable = false, length = 191)
    private String en;

    @Column(length = 64)
    private String ph;

    @Column(name = "ph_us", length = 64)
    private String phUs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
