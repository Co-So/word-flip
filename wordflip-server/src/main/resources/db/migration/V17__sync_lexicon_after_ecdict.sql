-- ===========================================================================
-- V17：ECDICT 重建后再次同步 user_word_lexicon primary 冗余字段
-- ===========================================================================

UPDATE user_word_lexicon u
INNER JOIN dict_words w ON w.word_key = u.word_key
INNER JOIN dict_senses s
    ON s.word_key = u.word_key
   AND s.is_primary = 1
   AND s.quality = 'ok'
SET
    u.cn = s.cn,
    u.pos = s.pos,
    u.ph = w.ph;
