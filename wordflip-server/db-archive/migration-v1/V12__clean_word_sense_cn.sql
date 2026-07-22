-- ===========================================================================
-- V12：词书释义清洗（测验题质量）
-- 字段职责：cn=中文释义正文；pos=词性；禁止把词性/英文短语碎片塞进 cn。
-- KyleBing ETL 残留：尾部 (n.)/(v.)、短语拆坏（in→favour…）等。
-- ===========================================================================

-- 1) 剥 cn 尾部词性标记，如「突然地 (adv.)」→「突然地」
UPDATE book_words
SET cn = TRIM(REGEXP_REPLACE(cn, '[[:space:]]*\\([A-Za-z&./ ]+\\.?\\)[[:space:]]*$', ''))
WHERE cn REGEXP '\\([A-Za-z&./ ]+\\.?\\)[[:space:]]*$';

UPDATE user_word_lexicon
SET cn = TRIM(REGEXP_REPLACE(cn, '[[:space:]]*\\([A-Za-z&./ ]+\\.?\\)[[:space:]]*$', ''))
WHERE cn REGEXP '\\([A-Za-z&./ ]+\\.?\\)[[:space:]]*$';

-- 2) 短语被错误拆成虚词头：修正为该词本身的常用释义（避免测验「无正确项」）
UPDATE book_words SET cn = '在；在…里；在…期间', pos = 'prep.'
WHERE word_key = 'in' AND cn LIKE 'favour%';

UPDATE book_words SET cn = '去；走；离开', pos = 'v.'
WHERE word_key = 'go' AND cn LIKE 'without%';

UPDATE book_words SET cn = '出；向外；离开', pos = 'adv.'
WHERE word_key = 'out' AND cn LIKE 'of%';

UPDATE book_words SET cn = '是；在；成为', pos = 'v.'
WHERE word_key = 'be' AND (cn LIKE 'at%' OR cn LIKE 'v&vi%' OR cn LIKE 'v %');

UPDATE book_words SET cn = '对…负有责任', pos = 'adj.'
WHERE word_key = 'accountable' AND cn LIKE 'for%';

UPDATE user_word_lexicon SET cn = '在；在…里；在…期间', pos = 'prep.'
WHERE word_key = 'in' AND cn LIKE 'favour%';

UPDATE user_word_lexicon SET cn = '去；走；离开', pos = 'v.'
WHERE word_key = 'go' AND cn LIKE 'without%';

UPDATE user_word_lexicon SET cn = '出；向外；离开', pos = 'adv.'
WHERE word_key = 'out' AND cn LIKE 'of%';

UPDATE user_word_lexicon SET cn = '是；在；成为', pos = 'v.'
WHERE word_key = 'be' AND (cn LIKE 'at%' OR cn LIKE 'v&vi%' OR cn LIKE 'v %');

UPDATE user_word_lexicon SET cn = '对…负有责任', pos = 'adj.'
WHERE word_key = 'accountable' AND cn LIKE 'for%';
