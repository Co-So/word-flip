-- 全局英文词频序（GroupStrategy.frequency；数据源 wordfreq/COCA）
CREATE TABLE word_freq_ranks (
    word_key   VARCHAR(128) NOT NULL COMMENT 'en.trim().toLowerCase()',
    freq_rank  INT UNSIGNED NOT NULL COMMENT '1=最高频',
    source     VARCHAR(32)  NOT NULL DEFAULT 'wordfreq' COMMENT '语料来源标识',
    PRIMARY KEY (word_key),
    KEY idx_word_freq_ranks_rank (freq_rank)
) COMMENT='全局英文词频序，供 frequency 分组策略排序';
