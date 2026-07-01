-- ===========================================================================
-- WordFlip MVP 数据库初始化
-- 对齐：docs/wordflip/database-design.md v1.0
-- 说明：表/字段使用 MySQL COMMENT 写入元数据；业务规则见 api-modules.md
-- ===========================================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ---------------------------------------------------------------------------
-- 模块：Auth / Settings（账号与用户偏好）
-- ---------------------------------------------------------------------------

-- 用户账号表（REQ-AUTH：邮箱或手机号至少一项）
CREATE TABLE users (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    email           VARCHAR(255)    NULL COMMENT '登录邮箱，唯一，可为空',
    phone           VARCHAR(20)     NULL COMMENT '登录手机号 E.164，唯一，可为空',
    password_hash   VARCHAR(72)     NOT NULL COMMENT 'BCrypt 密码哈希',
    status          ENUM('active', 'disabled') NOT NULL DEFAULT 'active' COMMENT '账号状态：active 正常 / disabled 禁用',
    timezone        VARCHAR(64)     NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '默认时区，用于计算「当日」边界',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
    last_login_at   DATETIME(3)     NULL COMMENT '最近登录时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone),
    CONSTRAINT chk_users_account CHECK (email IS NOT NULL OR phone IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户账号';

-- 用户设置（1:1 users；词书勾选在 user_book_selection）
CREATE TABLE user_settings (
    user_id                 BIGINT UNSIGNED NOT NULL COMMENT '用户 ID，PK/FK → users.id',
    group_size              TINYINT UNSIGNED NOT NULL DEFAULT 20 COMMENT '自动分组大小：10/20/30/50（REQ-BOOK-12）',
    auto_speak              TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '翻转时自动发音（REQ-SETTINGS-1）',
    theme_mode              ENUM('system', 'light', 'dark') NOT NULL DEFAULT 'system' COMMENT '外观主题（REQ-SETTINGS-7）',
    study_guide_completed   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已完成首次学习引导（REQ-STUDY-23）',
    reminder_enabled        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '每日提醒开关（规划项占位）',
    review_reminder_enabled TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '复习到期提醒（规划项占位）',
    updated_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户偏好与分组大小';

-- ---------------------------------------------------------------------------
-- 模块：Books / Lexicon（词书与用户词典）
-- ---------------------------------------------------------------------------

-- 词书：builtin 全员可见；imported 归属 user_id
CREATE TABLE books (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '词书主键',
    source          ENUM('builtin', 'imported') NOT NULL COMMENT '来源：builtin 内置 / imported 用户导入',
    user_id         BIGINT UNSIGNED NULL COMMENT 'imported 时必填；builtin 为 NULL',
    name            VARCHAR(64)     NOT NULL COMMENT '词书展示名称',
    declared_count  INT UNSIGNED    NULL COMMENT '声明词数（如「约 3000 词」），仅展示',
    word_count      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '该书去重后实际词条数（冗余维护）',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_books_user_name (user_id, name),
    KEY idx_books_source (source),
    CONSTRAINT fk_books_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_books_source CHECK (
        (source = 'builtin' AND user_id IS NULL)
        OR (source = 'imported' AND user_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='词书（内置 + 导入）';

-- 词书内词条；一书内 word_key 唯一（导入去重）
CREATE TABLE book_words (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '词条主键',
    book_id     BIGINT UNSIGNED NOT NULL COMMENT '所属词书 FK → books.id',
    word_key    VARCHAR(191)    NOT NULL COMMENT '归一化键 LOWER(TRIM(en))',
    en          VARCHAR(191)    NOT NULL COMMENT '展示用英文原文',
    cn          VARCHAR(512)    NOT NULL COMMENT '中文释义',
    pos         VARCHAR(32)     NULL COMMENT '词性',
    ph          VARCHAR(64)     NULL COMMENT '音标',
    detail_json JSON            NULL COMMENT '例句、词根等扩展（REQ-STUDY 抽屉）',
    sort_order  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '书内排序',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_book_words_book_key (book_id, word_key),
    KEY idx_book_words_word_key (word_key),
    CONSTRAINT fk_book_words_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='词书词条';

-- 用户勾选的词书（PUT /settings 全量替换；存在即选中）
CREATE TABLE user_book_selection (
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    book_id     BIGINT UNSIGNED NOT NULL COMMENT '词书 ID',
    selected_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近一次勾选时间 UTC',
    PRIMARY KEY (user_id, book_id),
    CONSTRAINT fk_ubs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ubs_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户词书勾选';

-- 用户学习域词典：测验判题、卡片展示的 cn/en 真相来源
CREATE TABLE user_word_lexicon (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    word_key        VARCHAR(191)    NOT NULL COMMENT '用户域唯一单词键',
    en              VARCHAR(191)    NOT NULL COMMENT '判题标准英文',
    cn              VARCHAR(512)    NOT NULL COMMENT '主释义',
    pos             VARCHAR(32)     NULL COMMENT '词性',
    ph              VARCHAR(64)     NULL COMMENT '音标',
    detail_json     JSON            NULL COMMENT '详情抽屉扩展 JSON',
    source_book_id  BIGINT UNSIGNED NULL COMMENT '首次来源词书（审计）',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lexicon_user_word (user_id, word_key),
    CONSTRAINT fk_lexicon_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_lexicon_source_book FOREIGN KEY (source_book_id) REFERENCES books (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户单词词典（学习域 canonical）';

-- ---------------------------------------------------------------------------
-- 模块：Groups（学习分组；一词一组）
-- ---------------------------------------------------------------------------

-- 学习分组：auto 增量追加 / custom 手动创建
CREATE TABLE groups (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分组主键',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    name        VARCHAR(64)     NOT NULL COMMENT '组名（auto：第 N 组；custom 可命名）',
    source      ENUM('auto', 'custom') NOT NULL COMMENT '来源：auto 词书保存追加 / custom 手动',
    status      ENUM('not_started', 'learning', 'completed') NOT NULL DEFAULT 'not_started' COMMENT '组状态（可运行时聚合回写）',
    sort_order  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '列表排序',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
    PRIMARY KEY (id),
    KEY idx_groups_user_created (user_id, created_at),
    KEY idx_groups_user_source (user_id, source),
    CONSTRAINT fk_groups_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='学习分组';

-- 分组-单词关联；uk_group_words_user_word 保证一词一组（REQ-BOOK-21）
CREATE TABLE group_words (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '冗余：支撑 (user_id, word_key) 唯一约束',
    group_id    BIGINT UNSIGNED NOT NULL COMMENT '分组 FK → groups.id',
    word_key    VARCHAR(191)    NOT NULL COMMENT '单词键',
    sort_order  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '组内排序',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '入组时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_words_user_word (user_id, word_key),
    UNIQUE KEY uk_group_words_group_word (group_id, word_key),
    KEY idx_group_words_group_sort (group_id, sort_order),
    CONSTRAINT fk_group_words_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_words_group FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='分组单词（一词一组）';

-- ---------------------------------------------------------------------------
-- 模块：SRS / Quiz（掌握度与测验；掌握度仅测验写入）
-- ---------------------------------------------------------------------------

-- 掌握度三态 + 是否有过测验历史（has_quiz_history 区分新词与 SRS 在档 unlearned）
CREATE TABLE word_mastery (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id             BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    word_key            VARCHAR(191)    NOT NULL COMMENT '单词键',
    level               ENUM('unlearned', 'fuzzy', 'unknown') NOT NULL DEFAULT 'unlearned'
                        COMMENT '掌握度：unlearned 未学习/测验通过在档 / fuzzy 单次答错 / unknown 连续第2次答错',
    has_quiz_history    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否有过测验记录（首次答题后恒为 1）',
    first_quiz_at       DATETIME(3)     NULL COMMENT '首次测验答题时间 UTC',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近 applyQuizResult 时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wm_user_word (user_id, word_key),
    KEY idx_wm_new_words (user_id, level, has_quiz_history),
    KEY idx_wm_quiz_pool (user_id, level),
    CONSTRAINT fk_wm_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='单词掌握度（仅测验写入）';

-- SRS 复习计划；与 word_mastery 1:1 (user_id, word_key)
CREATE TABLE review_plans (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    word_key        VARCHAR(191)    NOT NULL COMMENT '单词键',
    stage           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'SRS 阶段 0..5，对应间隔 [1,2,4,7,15,30] 天',
    next_review_at  DATE            NULL COMMENT '下次复习日（用户日历日）',
    last_quiz_at    DATETIME(3)     NULL COMMENT '最近测验答题时间 UTC',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rp_user_word (user_id, word_key),
    KEY idx_rp_due (user_id, next_review_at),
    KEY idx_rp_mastered (user_id, stage, next_review_at),
    CONSTRAINT fk_rp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='艾宾浩斯复习计划';

-- 默写测验会话（每次进入测验页新建，REQ-NAV-6）
CREATE TABLE quiz_sessions (
    id                CHAR(36)        NOT NULL COMMENT '会话 UUID',
    user_id           BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    source            ENUM('today', 'study', 'retry') NOT NULL DEFAULT 'today' COMMENT '入口：today / study / retry',
    group_id          BIGINT UNSIGNED NULL COMMENT 'study 源可选限定分组',
    status            ENUM('in_progress', 'completed') NOT NULL DEFAULT 'in_progress' COMMENT '会话状态',
    question_limit    INT             NOT NULL DEFAULT 10 COMMENT '请求出题上限',
    total_questions   INT             NOT NULL COMMENT '实际出题数',
    current_index     INT             NOT NULL DEFAULT 0 COMMENT '下一题题号',
    score             INT             NOT NULL DEFAULT 0 COMMENT '答对题数',
    started_at        DATETIME(3)     NOT NULL COMMENT '开始时间 UTC',
    completed_at      DATETIME(3)     NULL COMMENT '完成时间 UTC',
    PRIMARY KEY (id),
    KEY idx_qs_user_started (user_id, started_at DESC),
    KEY idx_qs_user_status (user_id, status),
    CONSTRAINT fk_qs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_qs_group FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='默写测验会话';

-- 会话题面快照（创建 session 时写入，防重复题号）
CREATE TABLE quiz_questions (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id      CHAR(36)        NOT NULL COMMENT '会话 FK → quiz_sessions.id',
    question_index  INT             NOT NULL COMMENT '题号（与 API questionIndex 一致）',
    word_key        VARCHAR(191)    NOT NULL COMMENT '单词键；同 session 内不重复',
    expected_en     VARCHAR(191)    NOT NULL COMMENT '判题标准答案快照',
    prompt_cn       VARCHAR(512)    NOT NULL COMMENT '中文题干',
    prompt_pos      VARCHAR(32)     NULL COMMENT '词性展示',
    prompt_ph       VARCHAR(64)     NULL COMMENT '音标展示',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qq_session_index (session_id, question_index),
    CONSTRAINT fk_qq_session FOREIGN KEY (session_id) REFERENCES quiz_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='测验题目快照';

-- 测验作答明细；idx_qa_user_word_time 用于连续答错判定
CREATE TABLE quiz_answers (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id                 BIGINT UNSIGNED NOT NULL COMMENT '用户 ID（冗余，连续错题查询）',
    session_id              CHAR(36)        NOT NULL COMMENT '会话 ID',
    question_id             BIGINT UNSIGNED NOT NULL COMMENT '题目 FK → quiz_questions.id',
    word_key                VARCHAR(191)    NOT NULL COMMENT '单词键',
    question_index          INT             NOT NULL COMMENT '题号',
    user_answer             VARCHAR(512)    NOT NULL COMMENT '用户输入（trim 后存储）',
    correct                 TINYINT(1)      NOT NULL COMMENT '是否答对（忽略大小写判题）',
    is_consecutive_wrong    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '本次是否触发连续第2次答错 → unknown',
    answered_at             DATETIME(3)     NOT NULL COMMENT '作答时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qa_session_index (session_id, question_index),
    KEY idx_qa_user_word_time (user_id, word_key, answered_at DESC, id DESC),
    KEY idx_qa_user_time (user_id, answered_at DESC),
    CONSTRAINT fk_qa_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_qa_session FOREIGN KEY (session_id) REFERENCES quiz_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_qa_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='测验作答记录';

-- ---------------------------------------------------------------------------
-- 模块：Media（卡拍图片与污渍）
-- ---------------------------------------------------------------------------

-- 卡片图片元数据；二进制在 MinIO：card-images/{userId}/{wordKey}.webp
CREATE TABLE word_images (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    word_key        VARCHAR(191)    NOT NULL COMMENT '单词键',
    storage_key     VARCHAR(512)    NOT NULL COMMENT 'MinIO 对象路径',
    transform_json  JSON            NOT NULL COMMENT '裁剪/旋转/滤镜等 transform 参数',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wi_user_word (user_id, word_key),
    CONSTRAINT fk_wi_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='单词卡片图片';

-- 卡片污渍配置；无行时客户端用 stableHash(userId+wordKey) 默认 seed
CREATE TABLE word_stains (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id             BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    word_key            VARCHAR(191)    NOT NULL COMMENT '单词键',
    hidden              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否隐藏污渍（REQ-STAIN-5）',
    stain_config_json   JSON            NULL COMMENT '污渍 seed/类型/位置等配置',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ws_user_word (user_id, word_key),
    CONSTRAINT fk_ws_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='单词卡片污渍';

-- ---------------------------------------------------------------------------
-- 模块：Stats（学习日志与成就）
-- ---------------------------------------------------------------------------

-- 按用户+日历日聚合；热力图、打卡、连续天数数据源
CREATE TABLE study_logs (
    user_id             BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    log_date            DATE            NOT NULL COMMENT '用户时区日历日',
    study_duration_sec  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '学习时长累计（秒）',
    words_viewed        INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '浏览单词数',
    quiz_answered       INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '测验作答题数',
    quiz_correct        INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '测验答对题数',
    activity_score      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '热力图分级用活跃度分',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
    PRIMARY KEY (user_id, log_date),
    CONSTRAINT fk_study_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='每日学习日志';

-- 成就定义 seed 数据（Flyway V2 插入）
CREATE TABLE achievement_definitions (
    id          VARCHAR(64)     NOT NULL COMMENT '成就 ID，如 streak_7',
    name        VARCHAR(128)    NOT NULL COMMENT '成就名称',
    description VARCHAR(512)    NOT NULL COMMENT '成就描述',
    icon_key    VARCHAR(64)     NOT NULL COMMENT 'Material 图标名',
    rule_json   JSON            NULL COMMENT '解锁规则 JSON',
    sort_order  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '列表排序',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='成就定义';

-- 用户已解锁成就
CREATE TABLE user_achievements (
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    achievement_id  VARCHAR(64)     NOT NULL COMMENT '成就 ID FK → achievement_definitions.id',
    unlocked_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '解锁时间 UTC',
    PRIMARY KEY (user_id, achievement_id),
    CONSTRAINT fk_ua_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ua_achievement FOREIGN KEY (achievement_id) REFERENCES achievement_definitions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户成就解锁记录';
