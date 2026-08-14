package com.wordflip.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wordflip.dto.group.CreateCustomGroupRequest;
import com.wordflip.dto.group.GroupDetail;
import com.wordflip.dto.today.RecentGroupDto;
import com.wordflip.dto.today.TodayDashboard;
import com.wordflip.exception.WordflipException;
import com.wordflip.service.GroupService;
import com.wordflip.service.TodayService;
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
 * 使用真实 MySQL 验证 Today 与 Groups 共用当前计划和权威掌握口径。
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TodayGroupsMySqlIntegrationTest {

    private static final long USER_ID = 8_805_001L;
    private static final long OTHER_USER_ID = 8_805_002L;
    private static final long ACTIVE_BOOK_ID = 8_805_011L;
    private static final long HISTORY_BOOK_ID = 8_805_012L;
    private static final long ACTIVE_PLAN_ID = 8_805_021L;
    private static final long HISTORY_PLAN_ID = 8_805_022L;
    private static final long OTHER_USER_PLAN_ID = 8_805_023L;
    private static final long ACTIVE_GROUP_ID = 8_805_061L;
    private static final long HISTORY_GROUP_ID = 8_805_062L;
    private static final long OTHER_USER_GROUP_ID = 8_805_063L;
    private static final long CARD_A_ID = 8_805_051L;
    private static final long CARD_B_ID = 8_805_052L;
    private static final long CARD_C_ID = 8_805_053L;
    private static final long CARD_D_ID = 8_805_054L;
    private static final long UNASSIGNED_CARD_ID = 8_805_055L;
    private static final String COMPLETED_QUIZ_ID = "00000000-0000-0000-0000-000008805201";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("wordflip_today_groups_test")
            .withUsername("wordflip")
            .withPassword("wordflip")
            // Windows Docker Desktop 冷启动 MySQL 8.4 可能超过默认等待时间。
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
    private TodayService todayService;

    @Autowired
    private GroupService groupService;

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
     * A/B/C/D 分别锁定间隔不足、最近错题和 choice-only 边界，且读取不得写记忆。
     */
    @Test
    void todayAndGroupsUseSameMasteryRuleWithoutMemoryWrites() {
        long cardMemoryBefore = countRows("card_skill_memory");
        long lexemeMemoryBefore = countRows("lexeme_skill_memory");
        long eventsBefore = countRows("review_events");

        TodayDashboard today = todayService.getDashboard(USER_ID, ZoneId.of("Asia/Shanghai"));
        GroupDetail group = groupService.getGroup(USER_ID, ACTIVE_GROUP_ID);

        assertThat(today.stats().masteredCount()).isEqualTo(1);
        assertThat(today.recentGroups()).extracting(RecentGroupDto::groupId)
                .contains(ACTIVE_GROUP_ID);
        assertThat(group.progress()).isEqualTo(0.25F);
        assertThat(countRows("card_skill_memory")).isEqualTo(cardMemoryBefore);
        assertThat(countRows("lexeme_skill_memory")).isEqualTo(lexemeMemoryBefore);
        assertThat(countRows("review_events")).isEqualTo(eventsBefore);
    }

    /**
     * 同一用户历史计划与其他用户当前计划的分组都不可见，对外统一为 NOT_FOUND。
     */
    @Test
    void historicalAndOtherUserGroupsAreIndistinguishableNotFound() {
        assertNotFound(HISTORY_GROUP_ID);
        assertNotFound(OTHER_USER_GROUP_ID);
    }

    /**
     * 混合未入组卡和已入组卡时必须整体失败，不得留下空组或部分成员。
     */
    @Test
    void mixedCustomGroupConflictLeavesNoGroupOrMembershipRows() {
        long groupsBefore = countRows("study_groups");
        long membershipsBefore = countRows("study_group_cards");

        assertThatThrownBy(() -> groupService.createCustomGroup(
                USER_ID,
                new CreateCustomGroupRequest(
                        List.of(UNASSIGNED_CARD_ID, CARD_A_ID),
                        "不应保留的分组"
                )
        )).isInstanceOfSatisfying(WordflipException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("CONFLICT"));

        assertThat(countRows("study_groups")).isEqualTo(groupsBefore);
        assertThat(countRows("study_group_cards")).isEqualTo(membershipsBefore);
    }

    /**
     * 重复的合法 cardId 以首次出现为准去重，真实唯一约束下只写一条成员关系。
     */
    @Test
    void duplicateValidCardIdsCreateOneMembership() {
        long groupsBefore = countRows("study_groups");
        long membershipsBefore = countRows("study_group_cards");

        GroupDetail created = groupService.createCustomGroup(
                USER_ID,
                new CreateCustomGroupRequest(
                        List.of(UNASSIGNED_CARD_ID, UNASSIGNED_CARD_ID),
                        "去重分组"
                )
        );

        assertThat(countRows("study_groups")).isEqualTo(groupsBefore + 1);
        assertThat(countRows("study_group_cards")).isEqualTo(membershipsBefore + 1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM study_group_cards WHERE group_id=? AND card_id=?",
                Integer.class,
                created.id(),
                UNASSIGNED_CARD_ID
        )).isEqualTo(1);
    }

    private void assertNotFound(long groupId) {
        assertThatThrownBy(() -> groupService.getGroup(USER_ID, groupId))
                .isInstanceOfSatisfying(WordflipException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("NOT_FOUND"));
    }

    private void seedFixtures() {
        seedUsersBooksAndCards();
        seedPlansAndGroups();
        seedMemoriesAndReviews();
        seedCompletedQuiz();
    }

    /**
     * 第五张卡专用于自定义分组用例，保持在当前计划内但不预先入组。
     */
    private void seedUsersBooksAndCards() {
        jdbc.batchUpdate(
                "INSERT INTO users (id, email, password_hash, status, timezone)"
                        + " VALUES (?, ?, ?, 'active', 'Asia/Shanghai')",
                List.of(
                        new Object[]{USER_ID, "today-groups-owner@example.test", "test-password-hash"},
                        new Object[]{OTHER_USER_ID, "today-groups-other@example.test", "test-password-hash"}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO books"
                        + " (id, code, name, source_type, visibility, status, published_card_count)"
                        + " VALUES (?, ?, ?, 'builtin', 'public', 'published', ?)",
                List.of(
                        new Object[]{ACTIVE_BOOK_ID, "today-groups-active", "当前计划词书", 5},
                        new Object[]{HISTORY_BOOK_ID, "today-groups-history", "历史计划词书", 0}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO lexemes (id, word_key, headword, language, status)"
                        + " VALUES (?, ?, ?, 'en', 'active')",
                List.of(
                        new Object[]{8_805_031L, "today-groups-a", "alpha"},
                        new Object[]{8_805_032L, "today-groups-b", "bravo"},
                        new Object[]{8_805_033L, "today-groups-c", "charlie"},
                        new Object[]{8_805_034L, "today-groups-d", "delta"},
                        new Object[]{8_805_035L, "today-groups-unassigned", "echo"}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO book_items"
                        + " (id, book_id, lexeme_id, sort_order, raw_headword, status)"
                        + " VALUES (?, ?, ?, ?, ?, 'ready')",
                List.of(
                        new Object[]{8_805_041L, ACTIVE_BOOK_ID, 8_805_031L, 0, "alpha"},
                        new Object[]{8_805_042L, ACTIVE_BOOK_ID, 8_805_032L, 1, "bravo"},
                        new Object[]{8_805_043L, ACTIVE_BOOK_ID, 8_805_033L, 2, "charlie"},
                        new Object[]{8_805_044L, ACTIVE_BOOK_ID, 8_805_034L, 3, "delta"},
                        new Object[]{8_805_045L, ACTIVE_BOOK_ID, 8_805_035L, 4, "echo"}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO learning_cards"
                        + " (id, book_item_id, version, status, published_at, created_by)"
                        + " VALUES (?, ?, 1, 'published', CURRENT_TIMESTAMP(3), 'integration-test')",
                List.of(
                        new Object[]{CARD_A_ID, 8_805_041L},
                        new Object[]{CARD_B_ID, 8_805_042L},
                        new Object[]{CARD_C_ID, 8_805_043L},
                        new Object[]{CARD_D_ID, 8_805_044L},
                        new Object[]{UNASSIGNED_CARD_ID, 8_805_045L}
                )
        );
    }

    private void seedPlansAndGroups() {
        jdbc.batchUpdate(
                "INSERT INTO user_learning_plans"
                        + " (id, user_id, book_id, status, daily_new_card_limit) VALUES (?, ?, ?, ?, 20)",
                List.of(
                        new Object[]{ACTIVE_PLAN_ID, USER_ID, ACTIVE_BOOK_ID, "active"},
                        new Object[]{HISTORY_PLAN_ID, USER_ID, HISTORY_BOOK_ID, "paused"},
                        new Object[]{OTHER_USER_PLAN_ID, OTHER_USER_ID, ACTIVE_BOOK_ID, "active"}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO user_settings (user_id, active_plan_id) VALUES (?, ?)",
                List.of(
                        new Object[]{USER_ID, ACTIVE_PLAN_ID},
                        new Object[]{OTHER_USER_ID, OTHER_USER_PLAN_ID}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO study_groups (id, plan_id, name, source, sort_order)"
                        + " VALUES (?, ?, ?, 'auto', 0)",
                List.of(
                        new Object[]{ACTIVE_GROUP_ID, ACTIVE_PLAN_ID, "当前计划分组"},
                        new Object[]{HISTORY_GROUP_ID, HISTORY_PLAN_ID, "历史计划分组"},
                        new Object[]{OTHER_USER_GROUP_ID, OTHER_USER_PLAN_ID, "其他用户分组"}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO study_group_cards (id, group_id, plan_id, card_id, sort_order)"
                        + " VALUES (?, ?, ?, ?, ?)",
                List.of(
                        new Object[]{8_805_071L, ACTIVE_GROUP_ID, ACTIVE_PLAN_ID, CARD_A_ID, 0},
                        new Object[]{8_805_072L, ACTIVE_GROUP_ID, ACTIVE_PLAN_ID, CARD_B_ID, 1},
                        new Object[]{8_805_073L, ACTIVE_GROUP_ID, ACTIVE_PLAN_ID, CARD_C_ID, 2},
                        new Object[]{8_805_074L, ACTIVE_GROUP_ID, ACTIVE_PLAN_ID, CARD_D_ID, 3}
                )
        );
    }

    /**
     * A 已掌握；B 建议间隔不足；C 最近一次错误；D 只有 choice 记忆。
     */
    private void seedMemoriesAndReviews() {
        jdbc.batchUpdate(
                "INSERT INTO card_skill_memory"
                        + " (id, user_id, card_id, skill, state, stability, scheduled_days, reps)"
                        + " VALUES (?, ?, ?, ?, 'review', 90, ?, 1)",
                List.of(
                        new Object[]{8_805_081L, USER_ID, CARD_A_ID, "dictation", 40},
                        new Object[]{8_805_082L, USER_ID, CARD_B_ID, "dictation", 20},
                        new Object[]{8_805_083L, USER_ID, CARD_C_ID, "dictation", 40},
                        new Object[]{8_805_084L, USER_ID, CARD_D_ID, "choice", 40}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO lexeme_skill_memory"
                        + " (id, user_id, lexeme_id, skill, familiarity, successful_reviews, failed_reviews)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                List.of(
                        new Object[]{8_805_091L, USER_ID, 8_805_031L, "dictation", 0.9, 1, 0},
                        new Object[]{8_805_092L, USER_ID, 8_805_032L, "dictation", 0.8, 1, 0},
                        new Object[]{8_805_093L, USER_ID, 8_805_033L, "dictation", 0.4, 1, 1},
                        new Object[]{8_805_094L, USER_ID, 8_805_034L, "choice", 0.9, 1, 0}
                )
        );
        jdbc.batchUpdate(
                "INSERT INTO review_events"
                        + " (id, request_id, user_id, plan_id, card_id, lexeme_id, skill,"
                        + " question_type, rating, correct, answered_at, old_state_json,"
                        + " new_state_json, fsrs_version)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '{}', '{}', '1.0.0')",
                List.of(
                        reviewEvent(8_805_101L, CARD_A_ID, 8_805_031L,
                                "dictation", "dictation", "good", true, "2026-08-10 10:00:00.000"),
                        reviewEvent(8_805_102L, CARD_B_ID, 8_805_032L,
                                "dictation", "dictation", "good", true, "2026-08-10 10:01:00.000"),
                        reviewEvent(8_805_103L, CARD_C_ID, 8_805_033L,
                                "dictation", "dictation", "good", true, "2026-08-10 10:02:00.000"),
                        reviewEvent(8_805_104L, CARD_C_ID, 8_805_033L,
                                "dictation", "dictation", "again", false, "2026-08-10 10:03:00.000"),
                        reviewEvent(8_805_105L, CARD_D_ID, 8_805_034L,
                                "choice", "choice_en_cn", "good", true, "2026-08-10 10:04:00.000")
                )
        );
    }

    private Object[] reviewEvent(
            long id,
            long cardId,
            long lexemeId,
            String skill,
            String questionType,
            String rating,
            boolean correct,
            String answeredAt
    ) {
        String requestId = "00000000-0000-0000-0000-" + String.format("%012d", id);
        return new Object[]{
                id, requestId, USER_ID, ACTIVE_PLAN_ID, cardId, lexemeId,
                skill, questionType, rating, correct, answeredAt
        };
    }

    /**
     * Today 必须通过已完成测验的题目卡片回溯当前计划分组。
     */
    private void seedCompletedQuiz() {
        jdbc.update(
                "INSERT INTO quiz_sessions"
                        + " (id, user_id, plan_id, status, source, question_count, score, completed_at)"
                        + " VALUES (?, ?, ?, 'completed', 'groups', 1, 1, ?)",
                COMPLETED_QUIZ_ID,
                USER_ID,
                ACTIVE_PLAN_ID,
                "2026-08-11 09:00:00.000"
        );
        jdbc.update(
                "INSERT INTO quiz_questions"
                        + " (id, session_id, card_id, lexeme_id, skill, question_type,"
                        + " prompt_json, answer_json, sort_order)"
                        + " VALUES (?, ?, ?, ?, 'dictation', 'dictation', '{}', '{}', 0)",
                8_805_111L,
                COMPLETED_QUIZ_ID,
                CARD_A_ID,
                8_805_031L
        );
    }

    private long countRows(String table) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }

    /**
     * 按外键引用方向从子表到父表清理本测试的 8_805_xxx 数据。
     */
    private void clearFixtures() {
        jdbc.update("DELETE FROM quiz_answers WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
        jdbc.update(
                "DELETE FROM quiz_questions WHERE session_id IN"
                        + " (SELECT id FROM quiz_sessions WHERE user_id IN (?, ?))",
                USER_ID,
                OTHER_USER_ID
        );
        jdbc.update("DELETE FROM quiz_sessions WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
        jdbc.update("DELETE FROM review_events WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
        jdbc.update("DELETE FROM card_skill_memory WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
        jdbc.update("DELETE FROM lexeme_skill_memory WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
        jdbc.update(
                "DELETE FROM study_group_cards WHERE plan_id IN (?, ?, ?)",
                ACTIVE_PLAN_ID,
                HISTORY_PLAN_ID,
                OTHER_USER_PLAN_ID
        );
        jdbc.update(
                "DELETE FROM study_groups WHERE plan_id IN (?, ?, ?)",
                ACTIVE_PLAN_ID,
                HISTORY_PLAN_ID,
                OTHER_USER_PLAN_ID
        );
        jdbc.update("DELETE FROM user_settings WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
        jdbc.update(
                "DELETE FROM user_learning_plans WHERE id IN (?, ?, ?)",
                ACTIVE_PLAN_ID,
                HISTORY_PLAN_ID,
                OTHER_USER_PLAN_ID
        );
        jdbc.update(
                "DELETE FROM learning_cards WHERE id IN (?, ?, ?, ?, ?)",
                CARD_A_ID,
                CARD_B_ID,
                CARD_C_ID,
                CARD_D_ID,
                UNASSIGNED_CARD_ID
        );
        jdbc.update(
                "DELETE FROM book_items WHERE id IN (?, ?, ?, ?, ?)",
                8_805_041L,
                8_805_042L,
                8_805_043L,
                8_805_044L,
                8_805_045L
        );
        jdbc.update("DELETE FROM books WHERE id IN (?, ?)", ACTIVE_BOOK_ID, HISTORY_BOOK_ID);
        jdbc.update(
                "DELETE FROM lexemes WHERE id IN (?, ?, ?, ?, ?)",
                8_805_031L,
                8_805_032L,
                8_805_033L,
                8_805_034L,
                8_805_035L
        );
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", USER_ID, OTHER_USER_ID);
    }
}
