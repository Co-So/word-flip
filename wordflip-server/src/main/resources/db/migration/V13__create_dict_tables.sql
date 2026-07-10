-- ===========================================================================
-- V13：全局词典表 dict_words / dict_senses / dict_examples（词库结构化 Phase C）
-- 对齐：docs/wordflip/database-design.md §6.0 · plans/lexicon-restructure.md
-- 释义真相：dict_senses；展示/测验默认 is_primary=1 且 quality=ok
-- ===========================================================================

CREATE TABLE dict_words (
    word_key    VARCHAR(191)    NOT NULL COMMENT '词头键 LOWER(TRIM(en))',
    en          VARCHAR(191)    NOT NULL COMMENT '英文展示原文',
    ph          VARCHAR(64)     NULL COMMENT '音标（英式或通用）',
    ph_us       VARCHAR(64)     NULL COMMENT '美音音标（可选）',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
    PRIMARY KEY (word_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='全局词头（Headword）';

CREATE TABLE dict_senses (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '义项主键',
    word_key    VARCHAR(191)    NOT NULL COMMENT 'FK → dict_words.word_key',
    pos         VARCHAR(32)     NULL COMMENT '词性；禁止写入 cn',
    cn          VARCHAR(512)    NOT NULL COMMENT '纯中文释义（须含汉字）',
    is_primary  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '主义项；合格词恰好一条为 1',
    sort_order  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '展示顺序',
    quality     ENUM('ok', 'uncertain', 'reject') NOT NULL DEFAULT 'ok' COMMENT '清洗质量；仅 ok 的 primary 可入测验池',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
    PRIMARY KEY (id),
    KEY idx_dict_senses_word (word_key, sort_order),
    KEY idx_dict_senses_primary (word_key, is_primary, quality),
    CONSTRAINT fk_dict_senses_word
        FOREIGN KEY (word_key) REFERENCES dict_words (word_key)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='词义项（Sense）；一词多义 1:n';

CREATE TABLE dict_examples (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '例句主键',
    sense_id    BIGINT UNSIGNED NOT NULL COMMENT 'FK → dict_senses.id',
    en          VARCHAR(512)    NOT NULL COMMENT '英文例句',
    cn          VARCHAR(512)    NULL COMMENT '中文翻译',
    sort_order  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '展示顺序',
    PRIMARY KEY (id),
    KEY idx_dict_examples_sense (sense_id, sort_order),
    CONSTRAINT fk_dict_examples_sense
        FOREIGN KEY (sense_id) REFERENCES dict_senses (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='义项例句；MVP 可空';
