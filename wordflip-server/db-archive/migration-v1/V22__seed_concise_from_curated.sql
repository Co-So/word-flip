-- V22：从精校派生简明学习版（每词最多 2 义；primary.cn 截断至 40 字）
-- plans/multi-dict.md

INSERT INTO dict_words (dict_id, word_key, en, ph, ph_us, created_at, updated_at)
SELECT 'wordflip_concise', w.word_key, w.en, w.ph, w.ph_us, w.created_at, w.updated_at
FROM dict_words w
WHERE w.dict_id = 'wordflip_curated'
  AND NOT EXISTS (
      SELECT 1 FROM dict_words c
      WHERE c.dict_id = 'wordflip_concise' AND c.word_key = w.word_key
  );

-- 先拷 primary
INSERT INTO dict_senses (dict_id, word_key, pos, cn, en_gloss, is_primary, sort_order, quality, created_at)
SELECT
    'wordflip_concise',
    s.word_key,
    s.pos,
    CASE
        WHEN s.cn IS NULL THEN NULL
        WHEN CHAR_LENGTH(s.cn) <= 40 THEN s.cn
        WHEN LOCATE('，', s.cn) > 0 AND LOCATE('，', s.cn) <= 40
            THEN TRIM(SUBSTRING_INDEX(s.cn, '，', 1))
        WHEN LOCATE(',', s.cn) > 0 AND LOCATE(',', s.cn) <= 40
            THEN TRIM(SUBSTRING_INDEX(s.cn, ',', 1))
        WHEN LOCATE('；', s.cn) > 0 AND LOCATE('；', s.cn) <= 40
            THEN TRIM(SUBSTRING_INDEX(s.cn, '；', 1))
        ELSE LEFT(s.cn, 40)
    END,
    NULL,
    1,
    0,
    s.quality,
    s.created_at
FROM dict_senses s
WHERE s.dict_id = 'wordflip_curated'
  AND s.is_primary = 1
  AND s.quality = 'ok'
  AND NOT EXISTS (
      SELECT 1 FROM dict_senses c
      WHERE c.dict_id = 'wordflip_concise' AND c.word_key = s.word_key AND c.is_primary = 1
  );

-- 再拷一条非 primary（若有），作第二义
INSERT INTO dict_senses (dict_id, word_key, pos, cn, en_gloss, is_primary, sort_order, quality, created_at)
SELECT
    'wordflip_concise',
    s.word_key,
    s.pos,
    CASE
        WHEN s.cn IS NULL THEN NULL
        WHEN CHAR_LENGTH(s.cn) <= 40 THEN s.cn
        ELSE LEFT(s.cn, 40)
    END,
    NULL,
    0,
    1,
    s.quality,
    s.created_at
FROM dict_senses s
INNER JOIN (
    SELECT word_key, MIN(id) AS id
    FROM dict_senses
    WHERE dict_id = 'wordflip_curated' AND is_primary = 0 AND quality = 'ok'
    GROUP BY word_key
) pick ON pick.id = s.id
WHERE NOT EXISTS (
    SELECT 1 FROM dict_senses c
    WHERE c.dict_id = 'wordflip_concise' AND c.word_key = s.word_key AND c.is_primary = 0
);
