package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.wordflip.domain.GroupStrategy;
import com.wordflip.dto.settings.PreferencesPatchRequest;
import com.wordflip.repository.UserSettingsRepository;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 使用真实 MySQL、JPA 仓储和 Spring 事务验证设置保存与 JDBC 追加的原子性。
 */
@SpringBootTest
@Testcontainers
class SettingsServiceMySqlIntegrationTest {

    private static final long USER_ID = 8_803_001L;
    private static final long BOOK_ID = 8_803_002L;
    private static final long PLAN_ID = 8_803_003L;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("wordflip_settings_test")
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
    private SettingsService settingsService;

    @Autowired
    private UserSettingsRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private GroupService groupService;

    @BeforeEach
    void setUp() {
        clearFixtures();
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, status, timezone)
                VALUES (?, ?, ?, 'active', 'Asia/Shanghai')
                """, USER_ID, "settings-transaction@example.test", "test-password-hash");
        jdbc.update("""
                INSERT INTO books
                    (id, code, name, source_type, visibility, status, published_card_count)
                VALUES (?, ?, ?, 'builtin', 'public', 'published', 0)
                """, BOOK_ID, "settings-transaction-book", "设置事务测试词书");
        jdbc.update("""
                INSERT INTO user_learning_plans
                    (id, user_id, book_id, status, daily_new_card_limit)
                VALUES (?, ?, ?, 'active', 20)
                """, PLAN_ID, USER_ID, BOOK_ID);
        jdbc.update("""
                INSERT INTO user_settings (user_id, active_plan_id, group_size, group_strategy)
                VALUES (?, ?, 20, 'book_order')
                """, USER_ID, PLAN_ID);
        assertThat(repository.findById(USER_ID)).isPresent();
    }

    @AfterEach
    void tearDown() {
        clearFixtures();
    }

    /**
     * append 执行前必须 flush，使同事务 JDBC 能读取新的分组配置。
     */
    @Test
    void flushesChangedGroupingBeforeAppendReadsItThroughJdbc() {
        doAnswer(invocation -> {
            assertDatabaseGrouping(30, "frequency");
            return null;
        }).when(groupService).appendAutoGroups(USER_ID, PLAN_ID);

        var response = settingsService.patchPreferences(USER_ID, changedGroupingRequest());

        assertThat(response.getGroupSize()).isEqualTo(30);
        assertThat(response.getGroupStrategy()).isEqualTo(GroupStrategy.frequency);
        assertDatabaseGrouping(30, "frequency");
    }

    /**
     * append 失败必须回滚已经 flush 的设置更新，保留原分组配置。
     */
    @Test
    void rollsBackFlushedGroupingWhenAppendFails() {
        RuntimeException appendFailure = new RuntimeException("追加失败");
        doThrow(appendFailure).when(groupService).appendAutoGroups(USER_ID, PLAN_ID);

        assertThatThrownBy(() -> settingsService.patchPreferences(USER_ID, changedGroupingRequest()))
                .isSameAs(appendFailure);

        assertDatabaseGrouping(20, "book_order");
    }

    private PreferencesPatchRequest changedGroupingRequest() {
        PreferencesPatchRequest request = new PreferencesPatchRequest();
        request.setGroupSize(30);
        request.setGroupStrategy(GroupStrategy.frequency);
        return request;
    }

    private void assertDatabaseGrouping(int groupSize, String groupStrategy) {
        Integer actualSize = jdbc.queryForObject(
                "SELECT group_size FROM user_settings WHERE user_id = ?",
                Integer.class,
                USER_ID
        );
        String actualStrategy = jdbc.queryForObject(
                "SELECT group_strategy FROM user_settings WHERE user_id = ?",
                String.class,
                USER_ID
        );
        assertThat(actualSize).isEqualTo(groupSize);
        assertThat(actualStrategy).isEqualTo(groupStrategy);
    }

    /**
     * 按外键引用关系从子表到父表清理固定测试数据。
     */
    private void clearFixtures() {
        jdbc.update("DELETE FROM study_group_cards WHERE plan_id = ?", PLAN_ID);
        jdbc.update("DELETE FROM study_groups WHERE plan_id = ?", PLAN_ID);
        jdbc.update("DELETE FROM user_settings WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM user_learning_plans WHERE id = ?", PLAN_ID);
        jdbc.update("DELETE FROM books WHERE id = ?", BOOK_ID);
        jdbc.update("DELETE FROM users WHERE id = ?", USER_ID);
    }
}
