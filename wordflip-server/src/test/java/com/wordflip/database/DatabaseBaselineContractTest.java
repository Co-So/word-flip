package com.wordflip.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 全新数据库基线契约测试。
 */
class DatabaseBaselineContractTest {

    /**
     * 新基线必须包含学习卡、学习计划、双层记忆与审计表，且不创建旧词典表。
     */
    @Test
    void v2BaselineContainsOnlyNewLearningModel() throws IOException {
        var resource = new ClassPathResource("db/migration-v2/V1__init_wordflip_v2.sql");
        assertThat(resource.exists()).isTrue();
        var sql = resource.getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(sql).contains(
                "create table content_sources",
                "create table lexemes",
                "create table books",
                "create table learning_cards",
                "create table user_learning_plans",
                "create table card_skill_memory",
                "create table lexeme_skill_memory",
                "create table review_events");
        assertThat(sql).doesNotContain(
                "create table dictionaries",
                "create table dict_words",
                "create table word_skill_progress",
                "active_dict_id");
    }
}
