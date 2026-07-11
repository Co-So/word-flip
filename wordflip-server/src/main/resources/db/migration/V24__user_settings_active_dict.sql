-- V24：用户当前词典偏好
-- REQ-LEX-9

ALTER TABLE user_settings
    ADD COLUMN active_dict_id VARCHAR(32) NOT NULL DEFAULT 'wordflip_curated'
        COMMENT '当前词典 FK → dictionaries.id' AFTER default_question_limit,
    ADD CONSTRAINT fk_user_settings_active_dict
        FOREIGN KEY (active_dict_id) REFERENCES dictionaries (id);
