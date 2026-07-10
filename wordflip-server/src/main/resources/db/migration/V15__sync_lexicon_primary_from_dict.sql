-- ===========================================================================
-- V15：用 dict primary 回填 user_word_lexicon 冗余字段（cn/pos/ph）
-- 进度键不变；仅刷新展示/判题用的 primary 缓存（REQ-LEX / Phase C）
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
