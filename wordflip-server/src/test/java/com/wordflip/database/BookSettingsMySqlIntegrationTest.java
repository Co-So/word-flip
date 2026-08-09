package com.wordflip.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.wordflip.dto.book.BookListResponse;
import com.wordflip.dto.settings.PreferencesPatchRequest;
import com.wordflip.service.BookService;
import com.wordflip.service.SettingsService;
import com.wordflip.service.StatsService;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * 使用真实 MySQL 验证词书计划进度隔离及设置更新的无学习副作用。
 */
@SpringBootTest
@Testcontainers
class BookSettingsMySqlIntegrationTest {

    private static final long USER_ID = 8_804_001L;
    private static final long OTHER_USER_ID = 8_804_002L;
    private static final long PAUSED_BOOK_ID = 8_804_011L;
    private static final long UNPLANNED_BOOK_ID = 8_804_012L;
    private static final long ACTIVE_BOOK_ID = 8_804_013L;
    private static final long ACTIVE_PLAN_ID = 8_804_021L;
    private static final long PAUSED_PLAN_ID = 8_804_022L;
    private static final long OTHER_ACTIVE_PLAN_ID = 8_804_023L;
    private static final long FIRST_CARD_ID = 8_804_051L;
    private static final long SECOND_CARD_ID = 8_804_052L;
    private static final long THIRD_CARD_ID = 8_804_053L;
    private static final long FOURTH_CARD_ID = 8_804_054L;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("wordflip_book_settings_test")
            .withUsername("wordflip")
            .withPassword("wordflip")
            // Windows Docker Desktop 冷启动 MySQL 8.4 可能超过默认 120 秒，放宽 JDBC 就绪等待。
            .withStartupTimeoutSeconds(300)
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void registerDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration-v2");
    }

    @Autowired
    private BookService bookService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private StatsService statsService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        clearFixtures();
        seedFixtures();
    }

    @AfterEach
    void tearDown() {
        clearFixtures();
    }

    /**
     * 当前计划优先于历史计划，无计划最后；进度只读取当前用户的听写记忆。
     */
    @Test
    void returnsActivePausedAndUnplannedBooksWithIsolatedDictationProgress() {
        List<BookListResponse.BookItem> books = bookService.listBooks(USER_ID).books().stream()
                .filter(book -> book.id() == ACTIVE_BOOK_ID
                        || book.id() == PAUSED_BOOK_ID
                        || book.id() == UNPLANNED_BOOK_ID)
                .toList();

        assertThat(books).extracting(BookListResponse.BookItem::id)
                .containsExactly(ACTIVE_BOOK_ID, PAUSED_BOOK_ID, UNPLANNED_BOOK_ID);
        assertThat(books).extracting(BookListResponse.BookItem::planStatus)
                .containsExactly("active", "paused", null);
        assertThat(books).extracting(BookListResponse.BookItem::selected)
                .containsExactly(true, false, false);
        assertThat(books.getFirst().planId()).isEqualTo(ACTIVE_PLAN_ID);
        assertThat(books.getFirst().progress())
                .isEqualTo(new BookListResponse.BookProgress(2, 4, 50));
        assertThat(statsService.summary(USER_ID, ZoneId.of("Asia/Shanghai")).masteredCount())
                .isEqualTo(books.getFirst().progress().masteredCount())
                .isEqualTo(2);
        assertThat(books.get(1).progress())
                .isEqualTo(new BookListResponse.BookProgress(0, 0, 0));
        assertThat(books.get(2).progress()).isNull();
    }

    /**
     * 偏好 patch 只能更新设置，不能写卡片记忆或复习审计事件。
     */
    @Test
    void settingsPatchDoesNotWriteMemoryOrReviewEvents() {
        int memoryRowsBefore = countRows("card_skill_memory");
        int reviewRowsBefore = countRows("review_events");
        PreferencesPatchRequest request = new PreferencesPatchRequest();
        request.setAutoSpeak(false);

        settingsService.patchPreferences(USER_ID, request);

        assertThat(countRows("card_skill_memory")).isEqualTo(memoryRowsBefore);
        assertThat(countRows("review_events")).isEqualTo(reviewRowsBefore);
    }

    private void seedFixtures() {
        jdbc.batchUpdate(
                "INSERT INTO users (id, email, password_hash, status, timezone)"
                        + " VALUES (?, ?, ?, 'active', 'Asia/Shanghai')",
                List.of(
                        new Object[]{USER_ID, "book-progress-owner@example.test", "test-password-hash"},
                        new Object[]{OTHER_USER_ID, "book-progress-other@example.test", "test-password-hash"}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO books"
                        + " (id, code, name, source_type, visibility, status, published_card_count)"
                        + " VALUES (?, ?, ?, 'builtin', 'public', 'published', ?)",
                List.of(
                        new Object[]{PAUSED_BOOK_ID, "book-progress-paused", "历史计划词书", 0},
                        new Object[]{UNPLANNED_BOOK_ID, "book-progress-unplanned", "未计划词书", 0},
                        new Object[]{ACTIVE_BOOK_ID, "book-progress-active", "当前计划词书", 4}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO lexemes (id, word_key, headword, language, status)"
                        + " VALUES (?, ?, ?, 'en', 'active')",
                List.of(
                        new Object[]{8_804_031L, "book-progress-first", "first"},
                        new Object[]{8_804_032L, "book-progress-second", "second"},
                        new Object[]{8_804_033L, "book-progress-third", "third"},
                        new Object[]{8_804_034L, "book-progress-fourth", "fourth"}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO book_items"
                        + " (id, book_id, lexeme_id, sort_order, raw_headword, status)"
                        + " VALUES (?, ?, ?, ?, ?, 'ready')",
                List.of(
                        new Object[]{8_804_041L, ACTIVE_BOOK_ID, 8_804_031L, 0, "first"},
                        new Object[]{8_804_042L, ACTIVE_BOOK_ID, 8_804_032L, 1, "second"},
                        new Object[]{8_804_043L, ACTIVE_BOOK_ID, 8_804_033L, 2, "third"},
                        new Object[]{8_804_044L, ACTIVE_BOOK_ID, 8_804_034L, 3, "fourth"}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO learning_cards"
                        + " (id, book_item_id, version, status, published_at, created_by)"
                        + " VALUES (?, ?, 1, 'published', CURRENT_TIMESTAMP(3), 'integration-test')",
                List.of(
                        new Object[]{FIRST_CARD_ID, 8_804_041L},
                        new Object[]{SECOND_CARD_ID, 8_804_042L},
                        new Object[]{THIRD_CARD_ID, 8_804_043L},
                        new Object[]{FOURTH_CARD_ID, 8_804_044L}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO user_learning_plans"
                        + " (id, user_id, book_id, status, daily_new_card_limit) VALUES (?, ?, ?, ?, 20)",
                List.of(
                        new Object[]{ACTIVE_PLAN_ID, USER_ID, ACTIVE_BOOK_ID, "active"},
                        new Object[]{PAUSED_PLAN_ID, USER_ID, PAUSED_BOOK_ID, "paused"},
                        new Object[]{OTHER_ACTIVE_PLAN_ID, OTHER_USER_ID, ACTIVE_BOOK_ID, "active"}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO user_settings (user_id, active_plan_id) VALUES (?, ?)",
                List.of(
                        new Object[]{USER_ID, ACTIVE_PLAN_ID},
                        new Object[]{OTHER_USER_ID, OTHER_ACTIVE_PLAN_ID}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO study_groups (id, plan_id, name, source, sort_order)"
                        + " VALUES (?, ?, ?, 'auto', 0)",
                List.of(
                        new Object[]{8_804_061L, ACTIVE_PLAN_ID, "当前计划分组"},
                        new Object[]{8_804_062L, OTHER_ACTIVE_PLAN_ID, "其他用户同书分组"}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO study_group_cards (id, group_id, plan_id, card_id, sort_order)"
                        + " VALUES (?, ?, ?, ?, ?)",
                List.of(
                        new Object[]{8_804_071L, 8_804_061L, ACTIVE_PLAN_ID, FIRST_CARD_ID, 0},
                        new Object[]{8_804_072L, 8_804_061L, ACTIVE_PLAN_ID, SECOND_CARD_ID, 1},
                        new Object[]{8_804_073L, 8_804_061L, ACTIVE_PLAN_ID, THIRD_CARD_ID, 2},
                        new Object[]{8_804_074L, 8_804_061L, ACTIVE_PLAN_ID, FOURTH_CARD_ID, 3},
                        new Object[]{8_804_075L, 8_804_062L, OTHER_ACTIVE_PLAN_ID, FOURTH_CARD_ID, 0}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO card_skill_memory"
                        + " (id, user_id, card_id, skill, state, stability, scheduled_days)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                List.of(
                        new Object[]{8_804_081L, USER_ID, FIRST_CARD_ID, "dictation", "review", 90, 40},
                        new Object[]{8_804_082L, USER_ID, SECOND_CARD_ID, "dictation", "review", 85, 35},
                        new Object[]{8_804_083L, USER_ID, THIRD_CARD_ID, "dictation", "review", 95, 45},
                        new Object[]{8_804_084L, USER_ID, FOURTH_CARD_ID, "dictation", "learning", 95, 45},
                        new Object[]{8_804_085L, USER_ID, FOURTH_CARD_ID, "choice", "review", 95, 45},
                        new Object[]{8_804_086L, OTHER_USER_ID, FOURTH_CARD_ID, "dictation", "review", 99, 60}
                )
        );
        seedReviewEvents();
    }

    /**
     * 第三张卡同一时间先正确后错误，以更大的事件主键确定最近结果为错误。
     */
    private void seedReviewEvents() {
        jdbc.batchUpdate(
                "INSERT INTO review_events"
                        + " (id, request_id, user_id, plan_id, card_id, lexeme_id, skill,"
                        + " question_type, rating, correct, answered_at, old_state_json,"
                        + " new_state_json, fsrs_version)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '{}', '{}', '1.0.0')",
                List.of(
                        new Object[]{8_804_091L, "00000000-0000-0000-0000-000008804091",
                                USER_ID, ACTIVE_PLAN_ID, FIRST_CARD_ID, 8_804_031L,
                                "dictation", "dictation", "good", true, "2026-08-01 10:00:00.000"},
                        new Object[]{8_804_092L, "00000000-0000-0000-0000-000008804092",
                                USER_ID, ACTIVE_PLAN_ID, SECOND_CARD_ID, 8_804_032L,
                                "dictation", "dictation", "good", true, "2026-08-01 10:00:00.000"},
                        new Object[]{8_804_093L, "00000000-0000-0000-0000-000008804093",
                                USER_ID, ACTIVE_PLAN_ID, THIRD_CARD_ID, 8_804_033L,
                                "dictation", "dictation", "good", true, "2026-08-02 10:00:00.000"},
                        new Object[]{8_804_094L, "00000000-0000-0000-0000-000008804094",
                                USER_ID, ACTIVE_PLAN_ID, THIRD_CARD_ID, 8_804_033L,
                                "dictation", "dictation", "again", false, "2026-08-02 10:00:00.000"},
                        new Object[]{8_804_095L, "00000000-0000-0000-0000-000008804095",
                                USER_ID, ACTIVE_PLAN_ID, FOURTH_CARD_ID, 8_804_034L,
                                "dictation", "dictation", "good", true, "2026-08-03 10:00:00.000"},
                        new Object[]{8_804_096L, "00000000-0000-0000-0000-000008804096",
                                USER_ID, ACTIVE_PLAN_ID, FOURTH_CARD_ID, 8_804_034L,
                                "choice", "choice_en_cn", "good", true, "2026-08-03 10:00:00.000"},
                        new Object[]{8_804_097L, "00000000-0000-0000-0000-000008804097",
                                OTHER_USER_ID, OTHER_ACTIVE_PLAN_ID, FOURTH_CARD_ID, 8_804_034L,
                                "dictation", "dictation", "good", true, "2026-08-04 10:00:00.000"}
                )
        );
    }

    private int countRows(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 按外键引用方向从子表到父表清理本测试固定数据。
     */
    private void clearFixtures() {
        jdbc.update("DELETE FROM review_events WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
        jdbc.update("DELETE FROM card_skill_memory WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
        jdbc.update(
                "DELETE FROM study_group_cards WHERE plan_id IN (?, ?, ?)",
                ACTIVE_PLAN_ID,
                PAUSED_PLAN_ID,
                OTHER_ACTIVE_PLAN_ID
        );
        jdbc.update(
                "DELETE FROM study_groups WHERE plan_id IN (?, ?, ?)",
                ACTIVE_PLAN_ID,
                PAUSED_PLAN_ID,
                OTHER_ACTIVE_PLAN_ID
        );
        jdbc.update("DELETE FROM user_settings WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
        jdbc.update(
                "DELETE FROM user_learning_plans WHERE id IN (?, ?, ?)",
                ACTIVE_PLAN_ID,
                PAUSED_PLAN_ID,
                OTHER_ACTIVE_PLAN_ID
        );
        jdbc.update(
                "DELETE FROM learning_cards WHERE id IN (?, ?, ?, ?)",
                FIRST_CARD_ID,
                SECOND_CARD_ID,
                THIRD_CARD_ID,
                FOURTH_CARD_ID
        );
        jdbc.update(
                "DELETE FROM book_items WHERE id IN (?, ?, ?, ?)",
                8_804_041L, 8_804_042L, 8_804_043L, 8_804_044L
        );
        jdbc.update("DELETE FROM books WHERE id IN (?, ?, ?)", PAUSED_BOOK_ID, UNPLANNED_BOOK_ID, ACTIVE_BOOK_ID);
        jdbc.update(
                "DELETE FROM lexemes WHERE id IN (?, ?, ?, ?)",
                8_804_031L, 8_804_032L, 8_804_033L, 8_804_034L
        );
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", USER_ID, OTHER_USER_ID);
    }
}
