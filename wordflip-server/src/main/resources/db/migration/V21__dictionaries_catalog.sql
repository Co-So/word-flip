-- V21：多词典目录 + dict_words/dict_senses 增加 dict_id；cn 可空 + en_gloss
-- REQ-LEX-9/10 · plans/multi-dict.md

CREATE TABLE dictionaries (
    id            VARCHAR(32)  NOT NULL COMMENT '词典 ID',
    name          VARCHAR(64)  NOT NULL COMMENT '展示名',
    locale        ENUM('zh', 'en') NOT NULL COMMENT 'zh=英汉；en=英英',
    license_note  VARCHAR(512) NULL COMMENT '许可/署名摘要',
    is_builtin    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否内置',
    sort_order    INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '列表排序',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='内置词典目录';

INSERT INTO dictionaries (id, name, locale, license_note, is_builtin, sort_order) VALUES
('wordflip_curated', 'WordFlip 精校', 'zh',
 'Based on ECDICT (MIT). Learning-primary curated for study/quiz.', 1, 0),
('wiktionary_zh', '维基词典', 'zh',
 'Derived from Wiktionary via Kaikki/Wiktextract (CC BY-SA). Attribution required.', 1, 1),
('wordflip_concise', '简明学习版', 'zh',
 'Derived from wordflip_curated: ≤2 senses, cn≤40 chars.', 1, 2),
('wordnet', 'WordNet 英英', 'en',
 'Princeton WordNet. See WordNet License; commercial use allowed with notice.', 1, 3);

-- 现有词头归入精校词典
ALTER TABLE dict_words
    ADD COLUMN dict_id VARCHAR(32) NOT NULL DEFAULT 'wordflip_curated'
        COMMENT '所属词典' AFTER word_key;

-- 重建 PK：(dict_id, word_key)；先去掉 senses FK
ALTER TABLE dict_senses DROP FOREIGN KEY fk_dict_senses_word;

ALTER TABLE dict_words DROP PRIMARY KEY;
ALTER TABLE dict_words
    ADD PRIMARY KEY (dict_id, word_key),
    ADD CONSTRAINT fk_dict_words_dict
        FOREIGN KEY (dict_id) REFERENCES dictionaries (id);

-- 义项挂 dict_id；cn 可空；英英 en_gloss
ALTER TABLE dict_senses
    ADD COLUMN dict_id VARCHAR(32) NOT NULL DEFAULT 'wordflip_curated'
        COMMENT '所属词典' AFTER id,
    ADD COLUMN en_gloss VARCHAR(512) NULL COMMENT '英英释义（WordNet）' AFTER cn;

ALTER TABLE dict_senses
    MODIFY COLUMN cn VARCHAR(512) NULL COMMENT '纯中文释义；英英可空';

ALTER TABLE dict_senses
    DROP INDEX idx_dict_senses_word,
    DROP INDEX idx_dict_senses_primary,
    ADD KEY idx_dict_senses_dict_word (dict_id, word_key, sort_order),
    ADD KEY idx_dict_senses_primary (dict_id, word_key, is_primary, quality),
    ADD CONSTRAINT fk_dict_senses_word
        FOREIGN KEY (dict_id, word_key) REFERENCES dict_words (dict_id, word_key)
        ON DELETE CASCADE;
