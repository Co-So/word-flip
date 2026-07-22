-- 多题型独立进度：dictation / choice 各一套 S + SRS（REQ skill 双轨）
CREATE TABLE word_skill_progress (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id               BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    word_key              VARCHAR(191)    NOT NULL COMMENT '单词键',
    skill                 ENUM('dictation', 'choice') NOT NULL COMMENT '题型技能：默写/选择',
    level                 ENUM('unlearned', 'fuzzy', 'unknown') NOT NULL DEFAULT 'unlearned' COMMENT '队列三态',
    has_quiz_history      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '该 skill 是否有过测验',
    first_quiz_at         DATETIME(3)     NULL COMMENT '该 skill 首次测验时间',
    stability             DECIMAL(6, 2)   NOT NULL DEFAULT 0.00 COMMENT '稳定性权值 S',
    window_correct_gain   DECIMAL(6, 2)   NOT NULL DEFAULT 0.00 COMMENT '短窗答对累计升幅',
    window_started_at     DATETIME(3)     NULL COMMENT '短窗起点',
    recent_wrong_count    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '短窗内答错次数',
    stage                 TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'SRS stage 0..5',
    next_review_at        DATE            NULL COMMENT '下次复习日',
    last_quiz_at          DATETIME(3)     NULL COMMENT '上次测验时间',
    updated_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wsp_user_word_skill (user_id, word_key, skill),
    KEY idx_wsp_due (user_id, skill, next_review_at),
    KEY idx_wsp_stability (user_id, skill, stability),
    KEY idx_wsp_level (user_id, skill, level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单词按题型的学习进度';

-- 将旧 mastery+plan 迁入 dictation skill
INSERT INTO word_skill_progress (
    user_id, word_key, skill, level, has_quiz_history, first_quiz_at,
    stability, window_correct_gain, window_started_at, recent_wrong_count,
    stage, next_review_at, last_quiz_at, updated_at
)
SELECT
    wm.user_id,
    wm.word_key,
    'dictation',
    wm.level,
    wm.has_quiz_history,
    wm.first_quiz_at,
    COALESCE(wm.stability, 0.00),
    COALESCE(wm.window_correct_gain, 0.00),
    wm.window_started_at,
    COALESCE(wm.recent_wrong_count, 0),
    COALESCE(rp.stage, 0),
    rp.next_review_at,
    rp.last_quiz_at,
    COALESCE(wm.updated_at, CURRENT_TIMESTAMP(3))
FROM word_mastery wm
LEFT JOIN review_plans rp
  ON rp.user_id = wm.user_id AND rp.word_key = wm.word_key;

-- 测验会话扩展：多组、题型、开测模式
ALTER TABLE quiz_sessions
    MODIFY COLUMN source ENUM('today', 'study', 'retry', 'groups', 'all', 'recent') NOT NULL DEFAULT 'today' COMMENT '测验入口来源',
    ADD COLUMN group_ids_json JSON NULL COMMENT '多组测验 groupId 列表' AFTER group_id,
    ADD COLUMN question_types_json JSON NULL COMMENT '本场题型列表' AFTER question_limit,
    ADD COLUMN launch_mode VARCHAR(32) NULL COMMENT 'mixed|free_select' AFTER question_types_json;

ALTER TABLE quiz_questions
    ADD COLUMN question_type ENUM('dictation', 'choice_en_cn', 'choice_cn_en') NOT NULL DEFAULT 'dictation' COMMENT '题型' AFTER word_key,
    ADD COLUMN options_json JSON NULL COMMENT '选择题选项 JSON' AFTER prompt_ph,
    ADD COLUMN correct_key VARCHAR(191) NULL COMMENT '选择题正确选项 key' AFTER options_json;

ALTER TABLE quiz_answers
    ADD COLUMN skill ENUM('dictation', 'choice') NOT NULL DEFAULT 'dictation' COMMENT '作答对应 skill' AFTER word_key,
    ADD COLUMN question_type ENUM('dictation', 'choice_en_cn', 'choice_cn_en') NOT NULL DEFAULT 'dictation' COMMENT '题型快照' AFTER skill;

-- 用户测验/热力偏好
ALTER TABLE user_settings
    ADD COLUMN heat_display_mode ENUM('combined', 'dictation', 'choice', 'free') NOT NULL DEFAULT 'combined' COMMENT '组详情热力展示模式' AFTER review_reminder_enabled,
    ADD COLUMN quiz_launch_mode ENUM('mixed', 'free_select') NOT NULL DEFAULT 'mixed' COMMENT '开测模式' AFTER heat_display_mode,
    ADD COLUMN default_question_limit TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '默认测验题数' AFTER quiz_launch_mode;

-- 最近学习组（今日页最多展示 3）
CREATE TABLE user_recent_groups (
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    group_id        BIGINT UNSIGNED NOT NULL COMMENT '分组 ID',
    last_studied_at DATETIME(3)     NOT NULL COMMENT '最近学习/测验时间',
    PRIMARY KEY (user_id, group_id),
    KEY idx_urg_user_time (user_id, last_studied_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户最近学习的分组';
