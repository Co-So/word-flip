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
 * 单词卡片污渍实体，对应 word_stains 表（REQ-STAIN-1~7）。
 * <p>
 * 无行时不落库，客户端/API 用 {@code stableHash(userId + wordKey)} 作为默认 seed。
 */
@Entity
@Table(name = "word_stains")
@Getter
@Setter
public class WordStain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "word_key", nullable = false, length = 191)
    private String wordKey;

    /** 是否隐藏污渍（REQ-STAIN-5） */
    @Column(nullable = false)
    private boolean hidden = false;

    /** 污渍 seed/类型/位置等 JSON；与 openapi StainConfig 对齐 */
    @Column(name = "stain_config_json", columnDefinition = "json")
    private String stainConfigJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
