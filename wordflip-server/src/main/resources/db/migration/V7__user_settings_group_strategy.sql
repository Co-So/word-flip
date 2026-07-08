-- 用户设置：自动分组策略（REQ-BOOK-22~24）
ALTER TABLE user_settings
    ADD COLUMN group_strategy ENUM('book_order', 'frequency', 'random') NOT NULL DEFAULT 'book_order'
        COMMENT '自动分组策略：词书顺序 / 词频（暂回退词书顺序）/ 随机'
        AFTER group_size;
