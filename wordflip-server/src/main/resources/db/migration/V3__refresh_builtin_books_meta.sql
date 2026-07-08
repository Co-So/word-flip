-- ===========================================================================
-- 清除内置词书占位词条，更新 word_count
-- ===========================================================================

DELETE FROM book_words WHERE book_id IN (1, 2, 3);

UPDATE books SET word_count = 5254 WHERE id = 1;
UPDATE books SET word_count = 4544 WHERE id = 2;
UPDATE books SET word_count = 5044 WHERE id = 3;
