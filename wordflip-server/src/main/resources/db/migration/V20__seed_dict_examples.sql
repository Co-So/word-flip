-- 为 ok primary 灌最低例句（学习向模板；后续可换人工/ECDICT 例句）
-- plans/dict-quality.md Q3

INSERT INTO dict_examples (sense_id, en, cn, sort_order)
SELECT
    s.id,
    CONCAT('I know the word "', w.en, '".'),
    CONCAT('我认识单词「',
           CASE
               WHEN CHAR_LENGTH(s.cn) <= 24 THEN s.cn
               ELSE LEFT(s.cn, 24)
           END,
           '」。'),
    0
FROM dict_senses s
INNER JOIN dict_words w ON w.word_key = s.word_key
WHERE s.is_primary = 1
  AND s.quality = 'ok'
  AND NOT EXISTS (
      SELECT 1 FROM dict_examples e WHERE e.sense_id = s.id
  );

-- 虚词/高频词人工例句（覆盖模板，提升测验观感）
DELETE e FROM dict_examples e
INNER JOIN dict_senses s ON s.id = e.sense_id
WHERE s.word_key IN (
    'only', 'but', 'just', 'go', 'as', 'however', 'because', 'if', 'when',
    'and', 'or', 'for', 'with', 'about', 'make', 'take', 'see', 'come', 'know'
)
AND e.sort_order = 0;

INSERT INTO dict_examples (sense_id, en, cn, sort_order)
SELECT s.id, v.en, v.cn, 0
FROM dict_senses s
INNER JOIN (
    SELECT 'only' AS word_key, 'I only need five minutes.' AS en, '我只需要五分钟。' AS cn
    UNION ALL SELECT 'but', 'I like tea, but she likes coffee.', '我喜欢茶，但是她喜欢咖啡。'
    UNION ALL SELECT 'just', 'He just arrived.', '他刚刚到。'
    UNION ALL SELECT 'go', 'I go to school every day.', '我每天去上学。'
    UNION ALL SELECT 'as', 'As I was leaving, it started to rain.', '当我正要离开时，开始下雨了。'
    UNION ALL SELECT 'however', 'He was tired; however, he kept working.', '他很累；然而，他继续工作。'
    UNION ALL SELECT 'because', 'I stayed home because it rained.', '因为下雨，我待在家里。'
    UNION ALL SELECT 'if', 'If it rains, we will stay.', '如果下雨，我们就留下。'
    UNION ALL SELECT 'when', 'Call me when you arrive.', '你到的时候给我打电话。'
    UNION ALL SELECT 'and', 'I like apples and oranges.', '我喜欢苹果和橙子。'
    UNION ALL SELECT 'or', 'Tea or coffee?', '茶还是咖啡？'
    UNION ALL SELECT 'for', 'This gift is for you.', '这份礼物是给你的。'
    UNION ALL SELECT 'with', 'I live with my family.', '我和家人住在一起。'
    UNION ALL SELECT 'about', 'We talked about the plan.', '我们谈论了那个计划。'
    UNION ALL SELECT 'make', 'She can make a cake.', '她会做蛋糕。'
    UNION ALL SELECT 'take', 'Please take a seat.', '请坐。'
    UNION ALL SELECT 'see', 'I see a bird in the tree.', '我看见树上有一只鸟。'
    UNION ALL SELECT 'come', 'Please come in.', '请进来。'
    UNION ALL SELECT 'know', 'I know the answer.', '我知道答案。'
) v ON v.word_key = s.word_key
WHERE s.is_primary = 1 AND s.quality = 'ok';
