-- WordFlip v2：词书专属学习卡 + FSRS 全新数据库基线。
-- 本目录用于空数据库；旧 db/migration 只作为历史归档，不参与运行。

CREATE TABLE users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键',
  email VARCHAR(320) NULL COMMENT '登录邮箱',
  phone VARCHAR(32) NULL COMMENT 'E.164 手机号',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码摘要',
  status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '用户状态：active/disabled',
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT 'IANA 时区',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  last_login_at DATETIME(3) NULL COMMENT '最近登录时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号';

CREATE TABLE content_sources (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内容来源主键',
  code VARCHAR(64) NOT NULL COMMENT '稳定来源编码，如 ecdict',
  name VARCHAR(128) NOT NULL COMMENT '来源名称',
  license_name VARCHAR(128) NULL COMMENT '许可证名称',
  license_url VARCHAR(500) NULL COMMENT '许可证地址',
  homepage_url VARCHAR(500) NULL COMMENT '来源主页',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_content_sources_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词典、词书原文和用户输入等内容来源';

CREATE TABLE source_revisions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '来源修订主键',
  source_id BIGINT UNSIGNED NOT NULL COMMENT '内容来源主键',
  version VARCHAR(64) NOT NULL COMMENT '来源版本',
  download_url VARCHAR(1000) NULL COMMENT '官方制品地址',
  sha256 CHAR(64) NULL COMMENT '官方制品 SHA-256',
  file_size BIGINT UNSIGNED NULL COMMENT '官方制品字节数',
  entry_count BIGINT UNSIGNED NULL COMMENT '校验词条数',
  manifest_json JSON NULL COMMENT '完整可复现 manifest',
  verified_at DATETIME(3) NULL COMMENT '完成校验时间（UTC）',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_source_revisions_source_version (source_id, version),
  CONSTRAINT fk_source_revisions_source FOREIGN KEY (source_id) REFERENCES content_sources(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容来源的可复现版本';

CREATE TABLE lexemes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '规范词形主键',
  word_key VARCHAR(191) NOT NULL COMMENT 'lower(trim(headword)) 查询键',
  headword VARCHAR(255) NOT NULL COMMENT '展示词头',
  language VARCHAR(16) NOT NULL DEFAULT 'en' COMMENT 'BCP 47 语言编码',
  phonetic VARCHAR(255) NULL COMMENT '通用音标',
  status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '词形状态',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lexemes_language_word (language, word_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨词书共享的规范词形';

CREATE TABLE source_entries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '原始来源词条主键',
  revision_id BIGINT UNSIGNED NOT NULL COMMENT '来源修订主键',
  lexeme_id BIGINT UNSIGNED NULL COMMENT '匹配后的规范词形主键',
  source_key VARCHAR(255) NOT NULL COMMENT '来源内部稳定键',
  raw_payload JSON NOT NULL COMMENT '来源原始行完整字段',
  raw_definition MEDIUMTEXT NULL COMMENT '原始英文释义',
  raw_translation MEDIUMTEXT NULL COMMENT '原始中文释义',
  match_status VARCHAR(24) NOT NULL DEFAULT 'matched' COMMENT 'matched/ambiguous/unmatched',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_source_entries_revision_key (revision_id, source_key),
  KEY idx_source_entries_lexeme_revision (lexeme_id, revision_id),
  CONSTRAINT fk_source_entries_revision FOREIGN KEY (revision_id) REFERENCES source_revisions(id),
  CONSTRAINT fk_source_entries_lexeme FOREIGN KEY (lexeme_id) REFERENCES lexemes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可破坏性覆盖的来源原始词条';

CREATE TABLE dictionary_senses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '来源义项主键',
  source_entry_id BIGINT UNSIGNED NOT NULL COMMENT '原始来源词条主键',
  pos VARCHAR(64) NULL COMMENT '规范词性',
  cn TEXT NULL COMMENT '结构化中文释义',
  en_gloss TEXT NULL COMMENT '结构化英文释义',
  quality VARCHAR(20) NOT NULL DEFAULT 'uncertain' COMMENT 'ok/uncertain/reject',
  sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '来源内义项顺序',
  derivation_json JSON NULL COMMENT '规则、覆盖文件和原始片段追溯信息',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  PRIMARY KEY (id),
  KEY idx_dictionary_senses_entry_order (source_entry_id, sort_order),
  CONSTRAINT fk_dictionary_senses_entry FOREIGN KEY (source_entry_id) REFERENCES source_entries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='由原始来源词条派生的结构化义项';

CREATE TABLE dictionary_examples (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '来源例句主键',
  sense_id BIGINT UNSIGNED NOT NULL COMMENT '来源义项主键',
  en TEXT NOT NULL COMMENT '英文例句',
  cn TEXT NULL COMMENT '中文翻译',
  sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '义项内例句顺序',
  PRIMARY KEY (id),
  KEY idx_dictionary_examples_sense_order (sense_id, sort_order),
  CONSTRAINT fk_dictionary_examples_sense FOREIGN KEY (sense_id) REFERENCES dictionary_senses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='来源义项例句';

CREATE TABLE word_forms (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '词形变化主键',
  lexeme_id BIGINT UNSIGNED NOT NULL COMMENT '规范词形主键',
  form VARCHAR(255) NOT NULL COMMENT '展示形式',
  form_key VARCHAR(191) NOT NULL COMMENT '规范化形式',
  form_type VARCHAR(32) NOT NULL COMMENT 'plural/past/variant 等',
  PRIMARY KEY (id),
  UNIQUE KEY uk_word_forms_lexeme_form_type (lexeme_id, form_key, form_type),
  CONSTRAINT fk_word_forms_lexeme FOREIGN KEY (lexeme_id) REFERENCES lexemes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='复数、时态与拼写变体';

CREATE TABLE books (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '词书主键',
  owner_user_id BIGINT UNSIGNED NULL COMMENT '用户私有词书所有者；公共词书为空',
  code VARCHAR(100) NOT NULL COMMENT '稳定词书编码',
  name VARCHAR(128) NOT NULL COMMENT '词书名称',
  source_type VARCHAR(24) NOT NULL COMMENT 'builtin/imported',
  visibility VARCHAR(24) NOT NULL DEFAULT 'public' COMMENT 'public/private',
  status VARCHAR(24) NOT NULL DEFAULT 'draft' COMMENT 'draft/published/archived',
  declared_count INT UNSIGNED NULL COMMENT '对外声明词数',
  published_card_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已发布学习卡数',
  content_version VARCHAR(64) NULL COMMENT '当前内容版本',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_books_owner_code (owner_user_id, code),
  KEY idx_books_public_status (visibility, status),
  CONSTRAINT fk_books_owner FOREIGN KEY (owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公共和用户私有词书';

CREATE TABLE book_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '词书条目主键',
  book_id BIGINT UNSIGNED NOT NULL COMMENT '词书主键',
  lexeme_id BIGINT UNSIGNED NOT NULL COMMENT '规范词形主键',
  sort_order INT UNSIGNED NOT NULL COMMENT '书内顺序',
  raw_headword VARCHAR(255) NOT NULL COMMENT '词书原始词头',
  raw_meaning TEXT NULL COMMENT '词书原始考义',
  status VARCHAR(24) NOT NULL DEFAULT 'pending' COMMENT 'pending/ready/review_required',
  metadata_json JSON NULL COMMENT '频率、章节等词书原始元数据',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_book_items_book_lexeme (book_id, lexeme_id),
  KEY idx_book_items_book_order (book_id, sort_order),
  CONSTRAINT fk_book_items_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  CONSTRAINT fk_book_items_lexeme FOREIGN KEY (lexeme_id) REFERENCES lexemes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词书成员、顺序与原始考义';

CREATE TABLE learning_cards (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '学习卡主键',
  book_item_id BIGINT UNSIGNED NOT NULL COMMENT '词书条目主键',
  version INT UNSIGNED NOT NULL COMMENT '卡片内容版本',
  status VARCHAR(24) NOT NULL DEFAULT 'draft' COMMENT 'draft/review_required/published/retired',
  published_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'published' THEN 1 ELSE NULL END) STORED COMMENT '保证每条目唯一发布版',
  published_at DATETIME(3) NULL COMMENT '发布时间（UTC）',
  created_by VARCHAR(100) NOT NULL COMMENT '构建工具或审核人',
  review_note TEXT NULL COMMENT '审核说明',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_learning_cards_item_version (book_item_id, version),
  UNIQUE KEY uk_learning_cards_one_published (book_item_id, published_slot),
  CONSTRAINT fk_learning_cards_item FOREIGN KEY (book_item_id) REFERENCES book_items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词书专属学习卡版本';

CREATE TABLE learning_card_senses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '学习卡义项主键',
  card_id BIGINT UNSIGNED NOT NULL COMMENT '学习卡主键',
  source_sense_id BIGINT UNSIGNED NULL COMMENT '引用的来源义项主键',
  pos VARCHAR(64) NULL COMMENT '卡片词性',
  cn TEXT NULL COMMENT '卡片中文考义',
  en_gloss TEXT NULL COMMENT '卡片英文考义',
  is_primary BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否为主义项',
  primary_slot TINYINT GENERATED ALWAYS AS (CASE WHEN is_primary THEN 1 ELSE NULL END) STORED COMMENT '保证每卡唯一主义项',
  quality VARCHAR(20) NOT NULL DEFAULT 'uncertain' COMMENT 'ok/uncertain/reject',
  sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '卡片内义项顺序',
  provenance_json JSON NULL COMMENT '词书、用户输入或来源义项追溯信息',
  PRIMARY KEY (id),
  UNIQUE KEY uk_learning_card_senses_primary (card_id, primary_slot),
  KEY idx_learning_card_senses_order (card_id, sort_order),
  CONSTRAINT fk_learning_card_senses_card FOREIGN KEY (card_id) REFERENCES learning_cards(id) ON DELETE CASCADE,
  CONSTRAINT fk_learning_card_senses_source FOREIGN KEY (source_sense_id) REFERENCES dictionary_senses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词书学习卡专属考义';

CREATE TABLE learning_card_examples (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '学习卡例句主键',
  card_sense_id BIGINT UNSIGNED NOT NULL COMMENT '学习卡义项主键',
  source_example_id BIGINT UNSIGNED NULL COMMENT '引用的来源例句主键',
  en TEXT NOT NULL COMMENT '英文例句',
  cn TEXT NULL COMMENT '中文翻译',
  sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '卡片义项内顺序',
  PRIMARY KEY (id),
  KEY idx_learning_card_examples_order (card_sense_id, sort_order),
  CONSTRAINT fk_learning_card_examples_sense FOREIGN KEY (card_sense_id) REFERENCES learning_card_senses(id) ON DELETE CASCADE,
  CONSTRAINT fk_learning_card_examples_source FOREIGN KEY (source_example_id) REFERENCES dictionary_examples(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习卡专属例句';

CREATE TABLE user_learning_plans (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '学习计划主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
  book_id BIGINT UNSIGNED NOT NULL COMMENT '词书主键',
  status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/paused/completed',
  daily_new_card_limit INT UNSIGNED NOT NULL DEFAULT 20 COMMENT '每日新卡上限',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_learning_plans_user_book (user_id, book_id),
  KEY idx_user_learning_plans_user_status (user_id, status),
  CONSTRAINT fk_user_learning_plans_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_learning_plans_book FOREIGN KEY (book_id) REFERENCES books(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户针对一本词书的长期学习计划';

CREATE TABLE user_settings (
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
  active_plan_id BIGINT UNSIGNED NULL COMMENT '唯一当前学习计划主键',
  group_size INT UNSIGNED NOT NULL DEFAULT 20 COMMENT '自动分组大小',
  group_strategy VARCHAR(24) NOT NULL DEFAULT 'book_order' COMMENT 'book_order/frequency/random',
  auto_speak BOOLEAN NOT NULL DEFAULT TRUE COMMENT '翻卡后自动发音',
  theme_mode VARCHAR(16) NOT NULL DEFAULT 'system' COMMENT 'system/light/dark',
  study_guide_completed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否完成学习页引导',
  reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '每日提醒开关',
  review_reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '到期复习提醒开关',
  heat_display_mode VARCHAR(16) NOT NULL DEFAULT 'combined' COMMENT 'combined/dictation/choice/free',
  quiz_launch_mode VARCHAR(24) NOT NULL DEFAULT 'mixed' COMMENT 'mixed/free_select',
  default_question_limit INT UNSIGNED NOT NULL DEFAULT 10 COMMENT '默认测验题数',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_settings_active_plan (active_plan_id),
  CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_settings_plan FOREIGN KEY (active_plan_id) REFERENCES user_learning_plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户显示偏好与当前学习计划指针';

CREATE TABLE study_groups (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '学习分组主键',
  plan_id BIGINT UNSIGNED NOT NULL COMMENT '学习计划主键',
  name VARCHAR(128) NOT NULL COMMENT '分组名称',
  source VARCHAR(16) NOT NULL DEFAULT 'auto' COMMENT 'auto/custom',
  sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '计划内分组顺序',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (id),
  KEY idx_study_groups_plan_order (plan_id, sort_order),
  CONSTRAINT fk_study_groups_plan FOREIGN KEY (plan_id) REFERENCES user_learning_plans(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单个学习计划内的自动或手动分组';

CREATE TABLE study_group_cards (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分组学习卡主键',
  group_id BIGINT UNSIGNED NOT NULL COMMENT '学习分组主键',
  plan_id BIGINT UNSIGNED NOT NULL COMMENT '冗余学习计划主键，用于唯一约束与查询',
  card_id BIGINT UNSIGNED NOT NULL COMMENT '学习卡主键',
  sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '分组内顺序',
  added_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_study_group_cards_plan_card (plan_id, card_id),
  KEY idx_study_group_cards_group_order (group_id, sort_order),
  CONSTRAINT fk_study_group_cards_group FOREIGN KEY (group_id) REFERENCES study_groups(id) ON DELETE CASCADE,
  CONSTRAINT fk_study_group_cards_plan FOREIGN KEY (plan_id) REFERENCES user_learning_plans(id) ON DELETE CASCADE,
  CONSTRAINT fk_study_group_cards_card FOREIGN KEY (card_id) REFERENCES learning_cards(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习计划分组中的学习卡';

CREATE TABLE lexeme_skill_memory (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '词形熟悉度主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
  lexeme_id BIGINT UNSIGNED NOT NULL COMMENT '规范词形主键',
  skill VARCHAR(16) NOT NULL COMMENT 'dictation/choice',
  familiarity DECIMAL(8,5) NOT NULL DEFAULT 0 COMMENT '跨书词形熟悉度，仅作诊断参考',
  last_review_at DATETIME(3) NULL COMMENT '最近有效答题时间（UTC）',
  successful_reviews INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计正确次数',
  failed_reviews INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计错误次数',
  version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lexeme_skill_memory_user_lexeme_skill (user_id, lexeme_id, skill),
  CONSTRAINT fk_lexeme_skill_memory_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_lexeme_skill_memory_lexeme FOREIGN KEY (lexeme_id) REFERENCES lexemes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨词书的词形熟悉度';

CREATE TABLE card_skill_memory (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '卡片 FSRS 记忆主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
  card_id BIGINT UNSIGNED NOT NULL COMMENT '学习卡主键',
  skill VARCHAR(16) NOT NULL COMMENT 'dictation/choice',
  state VARCHAR(16) NOT NULL DEFAULT 'new' COMMENT 'new/learning/review/relearning',
  step INT UNSIGNED NULL COMMENT '学习或重学步骤',
  stability DECIMAL(12,6) NOT NULL DEFAULT 0 COMMENT 'FSRS 稳定性（天）',
  difficulty DECIMAL(8,6) NOT NULL DEFAULT 0 COMMENT 'FSRS 难度',
  due_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次到期时间（UTC）',
  last_review_at DATETIME(3) NULL COMMENT '最近有效答题时间（UTC）',
  reps INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计复习次数',
  lapses INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计遗忘次数',
  elapsed_days INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本次复习前已过天数',
  scheduled_days INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上次计算的建议间隔天数',
  fsrs_version VARCHAR(32) NOT NULL DEFAULT '1.0.0' COMMENT '算法实现版本',
  version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_skill_memory_user_card_skill (user_id, card_id, skill),
  KEY idx_card_skill_memory_user_due (user_id, due_at, card_id),
  CONSTRAINT fk_card_skill_memory_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_card_skill_memory_card FOREIGN KEY (card_id) REFERENCES learning_cards(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='当前词书考义的权威 FSRS 调度状态';

CREATE TABLE quiz_sessions (
  id CHAR(36) NOT NULL COMMENT '测验会话 UUID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
  plan_id BIGINT UNSIGNED NOT NULL COMMENT '创建时的学习计划主键',
  status VARCHAR(20) NOT NULL DEFAULT 'in_progress' COMMENT 'in_progress/completed/abandoned',
  source VARCHAR(20) NOT NULL COMMENT 'today/study/retry/groups/all/recent/diagnostic',
  question_count INT UNSIGNED NOT NULL COMMENT '题目总数',
  score INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前得分',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  completed_at DATETIME(3) NULL COMMENT '完成时间（UTC）',
  PRIMARY KEY (id),
  KEY idx_quiz_sessions_user_created (user_id, created_at),
  CONSTRAINT fk_quiz_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_quiz_sessions_plan FOREIGN KEY (plan_id) REFERENCES user_learning_plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='固定到创建时学习计划的测验会话';

CREATE TABLE quiz_questions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '测验题目主键',
  session_id CHAR(36) NOT NULL COMMENT '测验会话 UUID',
  card_id BIGINT UNSIGNED NOT NULL COMMENT '学习卡主键',
  lexeme_id BIGINT UNSIGNED NOT NULL COMMENT '规范词形主键快照',
  skill VARCHAR(16) NOT NULL COMMENT 'dictation/choice',
  question_type VARCHAR(24) NOT NULL COMMENT 'dictation/choice_en_cn/choice_cn_en',
  prompt_json JSON NOT NULL COMMENT '不可变题面快照',
  answer_json JSON NOT NULL COMMENT '不可变标准答案快照',
  sort_order INT UNSIGNED NOT NULL COMMENT '会话内题号',
  PRIMARY KEY (id),
  UNIQUE KEY uk_quiz_questions_session_order (session_id, sort_order),
  CONSTRAINT fk_quiz_questions_session FOREIGN KEY (session_id) REFERENCES quiz_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_quiz_questions_card FOREIGN KEY (card_id) REFERENCES learning_cards(id),
  CONSTRAINT fk_quiz_questions_lexeme FOREIGN KEY (lexeme_id) REFERENCES lexemes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='带学习卡与词形快照的测验题目';

CREATE TABLE review_events (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '复习审计事件主键',
  request_id CHAR(36) NOT NULL COMMENT '客户端请求幂等 UUID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
  plan_id BIGINT UNSIGNED NOT NULL COMMENT '学习计划主键',
  card_id BIGINT UNSIGNED NOT NULL COMMENT '学习卡主键',
  lexeme_id BIGINT UNSIGNED NOT NULL COMMENT '规范词形主键',
  skill VARCHAR(16) NOT NULL COMMENT 'dictation/choice',
  question_type VARCHAR(24) NOT NULL COMMENT '具体题型',
  rating VARCHAR(12) NOT NULL COMMENT 'again/good',
  correct BOOLEAN NOT NULL COMMENT '服务端判题结果',
  answered_at DATETIME(3) NOT NULL COMMENT '有效答题时间（UTC）',
  old_state_json JSON NOT NULL COMMENT '更新前完整 FSRS 状态',
  new_state_json JSON NOT NULL COMMENT '更新后完整 FSRS 状态',
  fsrs_version VARCHAR(32) NOT NULL COMMENT '算法实现版本',
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_events_request (request_id),
  KEY idx_review_events_user_card_skill_time (user_id, card_id, skill, answered_at),
  CONSTRAINT fk_review_events_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_review_events_plan FOREIGN KEY (plan_id) REFERENCES user_learning_plans(id),
  CONSTRAINT fk_review_events_card FOREIGN KEY (card_id) REFERENCES learning_cards(id),
  CONSTRAINT fk_review_events_lexeme FOREIGN KEY (lexeme_id) REFERENCES lexemes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变的 FSRS 评分与状态变化审计日志';

CREATE TABLE quiz_answers (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '测验答案主键',
  request_id CHAR(36) NOT NULL COMMENT '客户端提交幂等 UUID',
  session_id CHAR(36) NOT NULL COMMENT '测验会话 UUID',
  question_id BIGINT UNSIGNED NOT NULL COMMENT '测验题目主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
  answer_json JSON NOT NULL COMMENT '用户答案快照',
  response_json JSON NOT NULL COMMENT '首次提交的完整响应快照',
  correct BOOLEAN NOT NULL COMMENT '服务端判题结果',
  review_event_id BIGINT UNSIGNED NOT NULL COMMENT '对应复习审计事件主键',
  answered_at DATETIME(3) NOT NULL COMMENT '答题时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_quiz_answers_request (request_id),
  UNIQUE KEY uk_quiz_answers_question (question_id),
  CONSTRAINT fk_quiz_answers_session FOREIGN KEY (session_id) REFERENCES quiz_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_quiz_answers_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id),
  CONSTRAINT fk_quiz_answers_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_quiz_answers_review_event FOREIGN KEY (review_event_id) REFERENCES review_events(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测验作答与复习事件关联';

CREATE TABLE card_images (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '卡片图片主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
  card_id BIGINT UNSIGNED NOT NULL COMMENT '学习卡主键',
  storage_key VARCHAR(500) NOT NULL COMMENT '对象存储键',
  transform_json JSON NULL COMMENT '裁剪、旋转和滤镜参数',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_images_user_card (user_id, card_id),
  CONSTRAINT fk_card_images_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_card_images_card FOREIGN KEY (card_id) REFERENCES learning_cards(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户针对学习卡的图片';

CREATE TABLE card_stains (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '卡片污渍主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
  card_id BIGINT UNSIGNED NOT NULL COMMENT '学习卡主键',
  hidden BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否隐藏',
  config_json JSON NOT NULL COMMENT '确定性污渍配置',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_stains_user_card (user_id, card_id),
  CONSTRAINT fk_card_stains_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_card_stains_card FOREIGN KEY (card_id) REFERENCES learning_cards(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户针对学习卡的污渍配置';

CREATE TABLE study_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '学习日志主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
  plan_id BIGINT UNSIGNED NOT NULL COMMENT '学习计划主键',
  group_id BIGINT UNSIGNED NULL COMMENT '学习分组主键',
  log_date DATE NOT NULL COMMENT '按用户时区计算的学习日期',
  duration_sec INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览时长秒数',
  cards_viewed INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览卡片数',
  quiz_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '有效答题数',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  PRIMARY KEY (id),
  KEY idx_study_logs_user_date (user_id, log_date),
  CONSTRAINT fk_study_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_study_logs_plan FOREIGN KEY (plan_id) REFERENCES user_learning_plans(id),
  CONSTRAINT fk_study_logs_group FOREIGN KEY (group_id) REFERENCES study_groups(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不改变记忆状态的学习与打卡日志';

CREATE TABLE achievement_definitions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '成就定义主键',
  code VARCHAR(64) NOT NULL COMMENT '稳定成就编码',
  name VARCHAR(128) NOT NULL COMMENT '成就名称',
  description VARCHAR(500) NOT NULL COMMENT '成就说明',
  rule_json JSON NOT NULL COMMENT '成就判定规则',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
  PRIMARY KEY (id),
  UNIQUE KEY uk_achievement_definitions_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成就规则定义';

CREATE TABLE user_achievements (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户成就主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
  achievement_id BIGINT UNSIGNED NOT NULL COMMENT '成就定义主键',
  plan_id BIGINT UNSIGNED NOT NULL COMMENT '解锁时学习计划主键',
  unlocked_at DATETIME(3) NOT NULL COMMENT '解锁时间（UTC）',
  snapshot_json JSON NOT NULL COMMENT '解锁时统计快照',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_achievements_user_achievement_plan (user_id, achievement_id, plan_id),
  CONSTRAINT fk_user_achievements_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_achievements_definition FOREIGN KEY (achievement_id) REFERENCES achievement_definitions(id),
  CONSTRAINT fk_user_achievements_plan FOREIGN KEY (plan_id) REFERENCES user_learning_plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户按学习计划解锁的成就';

INSERT INTO books(code, name, source_type, visibility, status, declared_count, content_version)
VALUES
  ('ielts', '雅思核心词汇', 'builtin', 'public', 'published', 5254, 'v1'),
  ('cet4', '四级高频词汇', 'builtin', 'public', 'published', 4544, 'v1'),
  ('kaoyan', '考研英语核心词', 'builtin', 'public', 'published', 5044, 'v1');
