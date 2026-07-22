package com.wordflip.service;

import com.wordflip.dto.stats.AchievementsResponse;
import com.wordflip.dto.stats.StatsHeatmapResponse;
import com.wordflip.dto.stats.StatsSummaryResponse;
import com.wordflip.exception.WordflipException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从不可变复习事件与学习日志聚合统计，不读取旧掌握度表。
 */
@Service
public class StatsService {

    private final JdbcTemplate jdbc;

    public StatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public StatsSummaryResponse summary(Long userId, ZoneId zoneId) {
        Long planId = currentPlanId(userId);
        int mastered = count("""
                SELECT COUNT(DISTINCT m.card_id) FROM card_skill_memory m
                JOIN study_group_cards sgc ON sgc.card_id=m.card_id AND sgc.plan_id=?
                WHERE m.user_id=? AND m.skill='dictation' AND m.stability>=30
                """, planId, userId);
        Instant since = Instant.now().minusSeconds(30L * 24 * 3600);
        int reviews = count(
                "SELECT COUNT(*) FROM review_events WHERE user_id=? AND plan_id=? AND answered_at>=?",
                userId, planId, Timestamp.from(since)
        );
        int correct = count(
                "SELECT COUNT(*) FROM review_events WHERE user_id=? AND plan_id=? AND answered_at>=? AND correct=TRUE",
                userId, planId, Timestamp.from(since)
        );
        int studyDays = count(
                "SELECT COUNT(DISTINCT log_date) FROM study_logs WHERE user_id=? AND plan_id=?",
                userId, planId
        );
        LocalDate today = LocalDate.now(zoneId);
        return new StatsSummaryResponse(
                mastered, streakDays(userId, planId, today), reviews == 0 ? 0 : (double) correct / reviews,
                studyDays
        );
    }

    @Transactional(readOnly = true)
    public StatsHeatmapResponse heatmap(Long userId, int months, ZoneId zoneId) {
        Long planId = currentPlanId(userId);
        LocalDate end = LocalDate.now(zoneId);
        LocalDate start = end.minusMonths(Math.min(Math.max(months, 1), 12)).plusDays(1);
        Map<LocalDate, Integer> counts = jdbc.query(
                """
                SELECT log_date, SUM(cards_viewed + quiz_count) AS activity
                  FROM study_logs WHERE user_id=? AND plan_id=? AND log_date BETWEEN ? AND ?
                 GROUP BY log_date
                """,
                (rs, row) -> Map.entry(rs.getDate("log_date").toLocalDate(), rs.getInt("activity")),
                userId, planId, Date.valueOf(start), Date.valueOf(end)
        ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<StatsHeatmapResponse.Day> days = start.datesUntil(end.plusDays(1))
                .map(date -> new StatsHeatmapResponse.Day(date, level(counts.getOrDefault(date, 0))))
                .toList();
        return new StatsHeatmapResponse(start, end, days);
    }

    @Transactional(readOnly = true)
    public AchievementsResponse achievements(Long userId) {
        Long planId = currentPlanId(userId);
        List<AchievementsResponse.Item> items = jdbc.query(
                """
                SELECT d.code, d.name, d.description, ua.unlocked_at
                  FROM achievement_definitions d
                  LEFT JOIN user_achievements ua ON ua.achievement_id=d.id
                   AND ua.user_id=? AND ua.plan_id=?
                 WHERE d.enabled=TRUE ORDER BY d.id
                """,
                (rs, row) -> {
                    Timestamp unlocked = rs.getTimestamp("unlocked_at");
                    return new AchievementsResponse.Item(
                            rs.getString("code"), rs.getString("name"), rs.getString("description"),
                            unlocked != null, unlocked == null ? null : unlocked.toInstant()
                    );
                },
                userId, planId
        );
        return new AchievementsResponse(items);
    }

    private int streakDays(Long userId, Long planId, LocalDate today) {
        List<LocalDate> dates = jdbc.query(
                """
                SELECT DISTINCT log_date FROM study_logs
                WHERE user_id=? AND plan_id=? AND log_date<=? ORDER BY log_date DESC
                """,
                (rs, row) -> rs.getDate(1).toLocalDate(), userId, planId, Date.valueOf(today)
        );
        int streak = 0;
        LocalDate expected = today;
        for (LocalDate date : dates) {
            if (!date.equals(expected)) {
                break;
            }
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }

    private Long currentPlanId(Long userId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT active_plan_id FROM user_settings WHERE user_id=? AND active_plan_id IS NOT NULL",
                Long.class, userId
        );
        if (ids.isEmpty()) {
            throw new WordflipException("NOT_FOUND", "尚未选择当前学习计划");
        }
        return ids.getFirst();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static int level(int activity) {
        if (activity <= 0) return 0;
        if (activity < 10) return 1;
        if (activity < 30) return 2;
        return 3;
    }
}
