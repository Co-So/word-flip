package com.wordflip.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 在一次性 MySQL 8 空库中验证 WordFlip v2 基线和应用映射。
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class WordFlipV2MySqlIntegrationTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "users", "content_sources", "source_revisions", "lexemes", "source_entries",
            "dictionary_senses", "dictionary_examples", "word_forms", "books", "book_items",
            "learning_cards", "learning_card_senses", "learning_card_examples", "user_learning_plans",
            "user_settings", "study_groups", "study_group_cards", "lexeme_skill_memory",
            "card_skill_memory", "quiz_sessions", "quiz_questions", "review_events", "quiz_answers",
            "card_images", "card_stains", "study_logs", "achievement_definitions", "user_achievements"
    );

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("wordflip_v2_test")
            .withUsername("wordflip")
            .withPassword("wordflip");

    @DynamicPropertySource
    static void registerDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration-v2");
    }

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Flyway 必须只创建新模型表，并为每张业务表设置表注释。
     */
    @Test
    void migratesEmptyDatabaseWithCommentsAndWithoutLegacyTables() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT table_name, table_comment
                  FROM information_schema.tables
                 WHERE table_schema=DATABASE() AND table_type='BASE TABLE'
                """);
        Set<String> names = rows.stream()
                .map(row -> String.valueOf(row.get("table_name")).toLowerCase())
                .collect(Collectors.toSet());

        assertThat(names).containsAll(EXPECTED_TABLES);
        assertThat(names).doesNotContain(
                "dictionaries", "dict_words", "dict_senses", "word_skill_progress",
                "group_words", "book_words"
        );
        assertThat(rows).filteredOn(row -> EXPECTED_TABLES.contains(
                        String.valueOf(row.get("table_name")).toLowerCase()))
                .allSatisfy(row -> assertThat(String.valueOf(row.get("table_comment"))).isNotBlank());
    }

    /**
     * 关键外键和唯一约束必须由真实 MySQL 解析并创建。
     */
    @Test
    void createsKeyForeignAndUniqueConstraints() {
        Set<String> foreignKeys = jdbc.queryForList("""
                SELECT constraint_name
                  FROM information_schema.referential_constraints
                 WHERE constraint_schema=DATABASE()
                """, String.class).stream().map(String::toLowerCase).collect(Collectors.toSet());
        assertThat(foreignKeys).contains(
                "fk_learning_cards_item", "fk_user_settings_plan",
                "fk_study_group_cards_card", "fk_review_events_card",
                "fk_card_images_card", "fk_card_stains_card"
        );

        Set<String> uniqueKeys = jdbc.queryForList("""
                SELECT DISTINCT index_name
                  FROM information_schema.statistics
                 WHERE table_schema=DATABASE() AND non_unique=0
                """, String.class).stream().map(String::toLowerCase).collect(Collectors.toSet());
        assertThat(uniqueKeys).contains(
                "uk_book_items_book_lexeme", "uk_learning_cards_one_published",
                "uk_study_group_cards_plan_card", "uk_review_events_request"
        );
    }
}
