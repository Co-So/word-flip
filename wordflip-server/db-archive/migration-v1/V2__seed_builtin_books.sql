-- ===========================================================================
-- WordFlip 种子数据
-- 内置词书占位（雅思 / 四级 / 考研）+ 成就定义
-- 正式词库数据后续可替换为完整导入脚本
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 内置词书（source=builtin，user_id 为空）
-- ---------------------------------------------------------------------------

INSERT INTO books (id, source, user_id, name, declared_count, word_count)
VALUES
    (1, 'builtin', NULL, '雅思核心词汇 3000', 3000, 5),
    (2, 'builtin', NULL, '四级高频词汇', 4500, 5),
    (3, 'builtin', NULL, '考研英语核心词', 5500, 5);

-- 每本 5 条示例词条（MVP 联调用；word_key = LOWER(TRIM(en))）
INSERT INTO book_words (book_id, word_key, en, cn, pos, ph, sort_order)
VALUES
    -- 雅思
    (1, 'abandon', 'abandon', '放弃；抛弃', 'v.', '/əˈbændən/', 1),
    (1, 'benefit', 'benefit', '利益；好处', 'n.', '/ˈbenɪfɪt/', 2),
    (1, 'candidate', 'candidate', '候选人；申请者', 'n.', '/ˈkændɪdət/', 3),
    (1, 'diverse', 'diverse', '多样的；不同的', 'adj.', '/daɪˈvɜːs/', 4),
    (1, 'essential', 'essential', '必要的；本质的', 'adj.', '/ɪˈsenʃl/', 5),
    -- 四级
    (2, 'ability', 'ability', '能力；才能', 'n.', '/əˈbɪləti/', 1),
    (2, 'balance', 'balance', '平衡；余额', 'n.', '/ˈbæləns/', 2),
    (2, 'challenge', 'challenge', '挑战', 'n.', '/ˈtʃælɪndʒ/', 3),
    (2, 'develop', 'develop', '发展；开发', 'v.', '/dɪˈveləp/', 4),
    (2, 'effort', 'effort', '努力', 'n.', '/ˈefət/', 5),
    -- 考研
    (3, 'abstract', 'abstract', '抽象的；摘要', 'adj.', '/ˈæbstrækt/', 1),
    (3, 'boundary', 'boundary', '边界；界限', 'n.', '/ˈbaʊndri/', 2),
    (3, 'concept', 'concept', '概念', 'n.', '/ˈkɒnsept/', 3),
    (3, 'dimension', 'dimension', '维度；尺寸', 'n.', '/daɪˈmenʃn/', 4),
    (3, 'evidence', 'evidence', '证据', 'n.', '/ˈevɪdəns/', 5);

-- ---------------------------------------------------------------------------
-- 成就定义（REQ-STATS-3）
-- ---------------------------------------------------------------------------

INSERT INTO achievement_definitions (id, name, description, icon_key, rule_json, sort_order)
VALUES
    ('streak_3', '连续打卡 3 天', '连续 3 天有学习记录', 'local_fire_department',
     '{"type":"streak","days":3}', 1),
    ('streak_7', '连续打卡 7 天', '连续 7 天有学习记录', 'local_fire_department',
     '{"type":"streak","days":7}', 2),
    ('streak_30', '连续打卡 30 天', '连续 30 天有学习记录', 'local_fire_department',
     '{"type":"streak","days":30}', 3),
    ('mastered_50', '掌握 50 词', '已掌握单词达到 50 个（stage>=5 且间隔>=30天）', 'emoji_events',
     '{"type":"mastered","count":50}', 10),
    ('mastered_100', '掌握 100 词', '已掌握单词达到 100 个', 'emoji_events',
     '{"type":"mastered","count":100}', 11),
    ('quiz_100', '测验百题', '累计完成 100 道测验题', 'quiz',
     '{"type":"quiz_answered","count":100}', 20);
