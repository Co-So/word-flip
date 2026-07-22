package com.wordflip.service;

import com.wordflip.dto.today.RecentGroupDto;
import com.wordflip.dto.today.RecommendedStudy;
import com.wordflip.dto.today.StudyReason;
import com.wordflip.dto.today.TaskSource;
import com.wordflip.dto.today.TodayDashboard;
import com.wordflip.dto.today.TodayStats;
import com.wordflip.dto.today.TodayTask;
import com.wordflip.dto.today.TodayTasks;
import com.wordflip.exception.WordflipException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 当前学习计划的今日任务聚合，复习到期时间完全来自卡片 FSRS。
 */
@Service
public class TodayService {

    private final JdbcTemplate jdbc;

    public TodayService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public TodayDashboard getDashboard(Long userId, ZoneId zoneId) {
        Long planId = currentPlanId(userId);
        LocalDate today = LocalDate.now(zoneId);
        Instant now = Instant.now();
        int total = count("SELECT COUNT(*) " + assignedCardsSql(), planId);
        int newCards = count("""
                SELECT COUNT(*) FROM study_group_cards sgc
                WHERE sgc.plan_id=? AND NOT EXISTS(
                  SELECT 1 FROM review_events r WHERE r.user_id=? AND r.card_id=sgc.card_id
                )
                """, planId, userId);
        int due = count("""
                SELECT COUNT(DISTINCT sgc.card_id) FROM study_group_cards sgc
                JOIN card_skill_memory m ON m.card_id=sgc.card_id AND m.user_id=?
                WHERE sgc.plan_id=? AND m.due_at<=?
                """, userId, planId, Timestamp.from(now));
        int mastered = count("""
                SELECT COUNT(DISTINCT sgc.card_id) FROM study_group_cards sgc
                JOIN card_skill_memory m ON m.card_id=sgc.card_id AND m.user_id=?
                WHERE sgc.plan_id=? AND m.skill='dictation' AND m.stability>=30
                """, userId, planId);
        int quizPool = count("""
                SELECT COUNT(DISTINCT sgc.card_id) FROM study_group_cards sgc
                LEFT JOIN card_skill_memory m ON m.card_id=sgc.card_id AND m.user_id=?
                WHERE sgc.plan_id=? AND (m.id IS NULL OR m.due_at<=?)
                """, userId, planId, Timestamp.from(now));

        List<TaskSource> newSources = groupSources(userId, planId, now, true);
        List<TaskSource> dueSources = groupSources(userId, planId, now, false);
        TodayTasks tasks = new TodayTasks(
                TodayTask.of(newCards, "新词学习", newSources),
                TodayTask.of(due, "到期复习", dueSources),
                TodayTask.of(quizPool, "学习卡测验")
        );
        TodayStats stats = new TodayStats(
                mastered, due, total == 0 ? 0 : Math.round((float) mastered / total * 100)
        );
        RecommendedStudy recommended = recommend(newSources, dueSources);
        return new TodayDashboard(
                today, streakDays(userId, today), stats, tasks, recommended, recentGroups(userId, planId)
        );
    }

    private List<TaskSource> groupSources(Long userId, Long planId, Instant now, boolean newOnly) {
        String condition = newOnly
                ? "NOT EXISTS(SELECT 1 FROM review_events r WHERE r.user_id=? AND r.card_id=sgc.card_id)"
                : "EXISTS(SELECT 1 FROM card_skill_memory m WHERE m.user_id=? AND m.card_id=sgc.card_id AND m.due_at<=?)";
        String sql = """
                SELECT g.id, g.name, COUNT(*) AS card_count
                  FROM study_groups g JOIN study_group_cards sgc ON sgc.group_id=g.id
                 WHERE g.plan_id=? AND %s
                 GROUP BY g.id, g.name ORDER BY card_count DESC, g.sort_order
                """.formatted(condition);
        return newOnly
                ? jdbc.query(sql, (rs, row) -> new TaskSource(
                        rs.getLong("id"), rs.getString("name"), rs.getInt("card_count")
                ), planId, userId)
                : jdbc.query(sql, (rs, row) -> new TaskSource(
                        rs.getLong("id"), rs.getString("name"), rs.getInt("card_count")
                ), planId, userId, Timestamp.from(now));
    }

    private RecommendedStudy recommend(List<TaskSource> newSources, List<TaskSource> dueSources) {
        if (!dueSources.isEmpty()) {
            TaskSource source = dueSources.getFirst();
            return new RecommendedStudy(source.groupId(), source.groupName(), source.count(), StudyReason.due_review);
        }
        if (!newSources.isEmpty()) {
            TaskSource source = newSources.getFirst();
            return new RecommendedStudy(source.groupId(), source.groupName(), source.count(), StudyReason.new_words);
        }
        return null;
    }

    private List<RecentGroupDto> recentGroups(Long userId, Long planId) {
        return jdbc.query(
                """
                SELECT g.id, g.name, MAX(sl.created_at) AS last_studied
                  FROM study_logs sl JOIN study_groups g ON g.id=sl.group_id
                 WHERE sl.user_id=? AND sl.plan_id=? AND sl.group_id IS NOT NULL
                 GROUP BY g.id, g.name ORDER BY last_studied DESC LIMIT 3
                """,
                (rs, row) -> new RecentGroupDto(
                        rs.getLong("id"), rs.getString("name"), rs.getTimestamp("last_studied").toInstant()
                ),
                userId, planId
        );
    }

    private int streakDays(Long userId, LocalDate today) {
        List<LocalDate> dates = jdbc.query(
                "SELECT DISTINCT log_date FROM study_logs WHERE user_id=? AND log_date<=? ORDER BY log_date DESC",
                (rs, row) -> rs.getDate(1).toLocalDate(), userId, Date.valueOf(today)
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

    private static String assignedCardsSql() {
        return "FROM study_group_cards sgc WHERE sgc.plan_id=?";
    }
}
