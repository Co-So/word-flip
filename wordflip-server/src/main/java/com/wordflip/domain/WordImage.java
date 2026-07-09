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
 * 单词卡片图片实体，对应 word_images 表（REQ-IMAGE / REQ-SNAPSHOT）。
 * <p>MinIO 路径约定：{@code card-images/{userId}/{wordKey}.webp}；transform 存 JSON。
 */
@Entity
@Table(name = "word_images")
@Getter
@Setter
public class WordImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "word_key", nullable = false, length = 191)
    private String wordKey;

    /** MinIO 对象路径 */
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    /** 裁剪/旋转/滤镜等 transform 参数（JSON 字符串） */
    @Column(name = "transform_json", nullable = false, columnDefinition = "json")
    private String transformJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
