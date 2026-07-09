-- 稳定性权值 S：组详情热力来源；仅测验写入（REQ-EBBING-8）
ALTER TABLE word_mastery
    ADD COLUMN stability DECIMAL(6, 2) NOT NULL DEFAULT 0.00 COMMENT '稳定性权值 S（0–100）' AFTER first_quiz_at,
    ADD COLUMN window_correct_gain DECIMAL(6, 2) NOT NULL DEFAULT 0.00 COMMENT '当前短窗内答对已计入升幅' AFTER stability,
    ADD COLUMN window_started_at DATETIME(3) NULL COMMENT '短窗起点' AFTER window_correct_gain,
    ADD COLUMN recent_wrong_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '短窗内答错次数' AFTER window_started_at,
    ADD KEY idx_wm_stability (user_id, stability);
