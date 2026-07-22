-- V23：WordNet 英英最小种子（联调/测验降级验收）；全量用 tools import-wordnet
-- Princeton WordNet License

INSERT INTO dict_words (dict_id, word_key, en, ph, ph_us, created_at, updated_at) VALUES
('wordnet', 'be', 'be', NULL, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('wordnet', 'have', 'have', NULL, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('wordnet', 'go', 'go', NULL, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('wordnet', 'only', 'only', NULL, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('wordnet', 'but', 'but', NULL, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('wordnet', 'just', 'just', NULL, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('wordnet', 'make', 'make', NULL, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('wordnet', 'take', 'take', NULL, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('wordnet', 'see', 'see', NULL, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('wordnet', 'know', 'know', NULL, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO dict_senses (dict_id, word_key, pos, cn, en_gloss, is_primary, sort_order, quality, created_at)
SELECT v.dict_id, v.word_key, v.pos, NULL, v.en_gloss, 1, 0, 'ok', CURRENT_TIMESTAMP(3)
FROM (
    SELECT 'wordnet' AS dict_id, 'be' AS word_key, 'v.' AS pos,
           'have the quality of being; (copula, used with an adjective or a predicate noun)' AS en_gloss
    UNION ALL SELECT 'wordnet', 'have', 'v.', 'have or possess, either in a concrete or an abstract sense'
    UNION ALL SELECT 'wordnet', 'go', 'v.', 'change location; move, travel, or proceed, also metaphorically'
    UNION ALL SELECT 'wordnet', 'only', 'adv.', 'and nothing more; exclusively'
    UNION ALL SELECT 'wordnet', 'but', 'conj.', 'and nothing more; however'
    UNION ALL SELECT 'wordnet', 'just', 'adv.', 'and nothing more; only a moment ago'
    UNION ALL SELECT 'wordnet', 'make', 'v.', 'engage in; give rise to; create or manufacture'
    UNION ALL SELECT 'wordnet', 'take', 'v.', 'carry out; get into one''s hands, take physically'
    UNION ALL SELECT 'wordnet', 'see', 'v.', 'perceive by sight or have the power to perceive by sight'
    UNION ALL SELECT 'wordnet', 'know', 'v.', 'be cognizant or aware of a fact or a specific piece of information'
) v
WHERE NOT EXISTS (
    SELECT 1 FROM dict_senses s
    WHERE s.dict_id = v.dict_id AND s.word_key = v.word_key AND s.is_primary = 1
);
