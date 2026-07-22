package com.wordflip.repository;

import com.wordflip.dto.learning.LearningPlanResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 使用显式 SQL 实现学习计划行锁和唯一当前指针。
 */
@Repository
public class JdbcLearningPlanStore implements LearningPlanStore {

    private static final String PLAN_SELECT = """
            SELECT p.id, p.book_id, b.name AS book_name, p.status,
                   p.daily_new_card_limit, p.created_at,
                   CASE WHEN s.active_plan_id = p.id THEN TRUE ELSE FALSE END AS active
              FROM user_learning_plans p
              JOIN books b ON b.id = p.book_id
              LEFT JOIN user_settings s ON s.user_id = p.user_id
            """;

    private final JdbcTemplate jdbc;

    public JdbcLearningPlanStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isBookVisible(Long userId, Long bookId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM books WHERE id=? AND status='published' AND (visibility='public' OR owner_user_id=?)",
                Integer.class,
                bookId,
                userId
        );
        return count != null && count > 0;
    }

    @Override
    public Optional<LearningPlanResponse> findByUserAndBook(Long userId, Long bookId) {
        return single(PLAN_SELECT + " WHERE p.user_id=? AND p.book_id=?", userId, bookId);
    }

    @Override
    public Optional<LearningPlanResponse> findOwnedPlan(Long userId, Long planId) {
        return single(PLAN_SELECT + " WHERE p.user_id=? AND p.id=?", userId, planId);
    }

    @Override
    public Optional<LearningPlanResponse> findCurrent(Long userId) {
        return single(PLAN_SELECT + " WHERE p.user_id=? AND s.active_plan_id=p.id", userId);
    }

    @Override
    public LearningPlanResponse create(Long userId, Long bookId, int dailyNewCardLimit) {
        jdbc.update(
                "INSERT INTO user_learning_plans(user_id, book_id, status, daily_new_card_limit) VALUES (?, ?, 'active', ?)",
                userId,
                bookId,
                dailyNewCardLimit
        );
        return findByUserAndBook(userId, bookId).orElseThrow();
    }

    @Override
    public void activate(Long userId, Long planId) {
        // 先保证设置行存在，再用 FOR UPDATE 串行化同一用户的计划切换。
        jdbc.update(
                "INSERT INTO user_settings(user_id) VALUES (?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",
                userId
        );
        jdbc.query("SELECT user_id FROM user_settings WHERE user_id=? FOR UPDATE", rs -> { }, userId);
        jdbc.update("UPDATE user_learning_plans SET status='active' WHERE id=? AND user_id=?", planId, userId);
        jdbc.update("UPDATE user_settings SET active_plan_id=? WHERE user_id=?", planId, userId);
    }

    @Override
    public void update(Long userId, Long planId, Integer dailyNewCardLimit, String status) {
        if (dailyNewCardLimit != null) {
            jdbc.update(
                    "UPDATE user_learning_plans SET daily_new_card_limit=? WHERE id=? AND user_id=?",
                    dailyNewCardLimit,
                    planId,
                    userId
            );
        }
        if (status != null) {
            jdbc.update(
                    "UPDATE user_learning_plans SET status=? WHERE id=? AND user_id=?",
                    status,
                    planId,
                    userId
            );
        }
    }

    private Optional<LearningPlanResponse> single(String sql, Object... args) {
        List<LearningPlanResponse> values = jdbc.query(sql, this::mapPlan, args);
        return values.stream().findFirst();
    }

    private LearningPlanResponse mapPlan(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LearningPlanResponse(
                resultSet.getLong("id"),
                resultSet.getLong("book_id"),
                resultSet.getString("book_name"),
                resultSet.getString("status"),
                resultSet.getInt("daily_new_card_limit"),
                resultSet.getBoolean("active"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
