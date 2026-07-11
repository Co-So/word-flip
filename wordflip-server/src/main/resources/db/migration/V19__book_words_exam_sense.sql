-- 词书考义绑定：导入/内置词挂 exam_sense_id → dict_senses（缺省=全局 primary）
-- REQ-LEX-7 / plans/dict-quality.md Q4

ALTER TABLE book_words
    ADD COLUMN exam_sense_id BIGINT UNSIGNED NULL
        COMMENT '词书考义义项 FK → dict_senses.id；NULL 则用全局 primary'
        AFTER detail_json,
    ADD KEY idx_book_words_exam_sense (exam_sense_id),
    ADD CONSTRAINT fk_book_words_exam_sense
        FOREIGN KEY (exam_sense_id) REFERENCES dict_senses (id) ON DELETE SET NULL;

-- 回填：已有词条绑定当前 learning-primary，并同步冗余 cn/pos
UPDATE book_words bw
INNER JOIN dict_senses s
    ON s.word_key = bw.word_key
   AND s.is_primary = 1
   AND s.quality = 'ok'
SET bw.exam_sense_id = s.id,
    bw.cn = s.cn,
    bw.pos = s.pos;
