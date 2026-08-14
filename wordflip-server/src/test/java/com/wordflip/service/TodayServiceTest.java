package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.wordflip.dto.today.TodayDashboard;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 验证今日页仅聚合当前计划的权威统计与最近活动。
 */
@ExtendWith(MockitoExtension.class)
class TodayServiceTest {

    private static final long USER_ID = 71L;
    private static final long PLAN_ID = 701L;

    @Mock
    private JdbcTemplate jdbc;

    private TodayService service;
    private List<QueryCall> countCalls;
    private List<QueryCall> rowQueryCalls;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new TodayService(jdbc);
        countCalls = new ArrayList<>();
        rowQueryCalls = new ArrayList<>();

        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(PLAN_ID));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    Object[] args = Arrays.copyOfRange(
                            invocation.getArguments(),
                            2,
                            invocation.getArguments().length
                    );
                    countCalls.add(new QueryCall(sql, args));
                    String normalizedSql = normalize(sql);
                    if (normalizedSql.contains("m.skill='dictation'")) {
                        return 2;
                    }
                    if (normalizedSql.contains("LEFTJOINcard_skill_memory")) {
                        return 4;
                    }
                    if (normalizedSql.contains("JOINcard_skill_memory")
                            && normalizedSql.contains("m.due_at<=?")) {
                        return 2;
                    }
                    if (normalizedSql.equals(
                            "SELECTCOUNT(*)FROMstudy_group_cardssgcWHEREsgc.plan_id=?"
                    )) {
                        return 3;
                    }
                    return 1;
                });
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    Object[] args = Arrays.copyOfRange(
                            invocation.getArguments(),
                            2,
                            invocation.getArguments().length
                    );
                    rowQueryCalls.add(new QueryCall(sql, args));
                    return List.of();
                });
    }

    /**
     * 掌握数必须使用当前计划中最近一次听写成功的完整四条件口径。
     */
    @Test
    void dashboardUsesAuthoritativeMasteryRuleAndLatestReview() {
        TodayDashboard response = dashboard();

        assertThat(response.stats().masteredCount()).isEqualTo(2);
        QueryCall call = countCalls.stream()
                .filter(item -> normalize(item.sql()).contains("m.skill='dictation'"))
                .findFirst()
                .orElseThrow();
        String normalizedSql = normalize(call.sql());
        assertThat(normalizedSql).contains(
                "sgc.plan_id=?",
                "m.user_id=?",
                "m.skill='dictation'",
                "m.state='review'",
                "m.stability>=80",
                "m.scheduled_days>=30",
                "r.correct=TRUE",
                "ORDERBYr2.answered_atDESC,r2.idDESCLIMIT1"
        );
        assertThat(call.args()).containsExactly(USER_ID, USER_ID, PLAN_ID);
    }

    /**
     * 统计卡和任务行必须复用同一个到期数，避免页面自相矛盾。
     */
    @Test
    void dashboardKeepsDueReviewStatsAndTaskCountConsistent() {
        TodayDashboard response = dashboard();

        assertThat(response.stats().dueReviewCount())
                .isEqualTo(response.tasks().dueReview().count());
        assertThat(countCalls.stream().filter(call -> isDueCountSql(call.sql())))
                .hasSize(1);
    }

    /**
     * 完成度以已掌握数除以当前计划已入组卡片总数并四舍五入。
     */
    @Test
    void dashboardRoundsCompletionPercentFromAssignedCards() {
        TodayDashboard response = dashboard();

        assertThat(response.stats().completionPercent()).isEqualTo(67);
    }

    /**
     * Dashboard 日期必须使用请求时区，且连续打卡仅查当前计划的该日及以前日志。
     */
    @Test
    void dashboardDateAndStreakUseRequestedZoneAndCurrentPlan() {
        TodayDashboard east = dashboardAndAssertDateBinding(ZoneId.of("Pacific/Kiritimati"));
        TodayDashboard west = dashboardAndAssertDateBinding(ZoneId.of("Pacific/Pago_Pago"));

        assertThat(east.date()).isNotEqualTo(west.date());
    }

    /**
     * 最近分组必须同时纳入学习日志和已完成测验活动。
     */
    @Test
    void recentGroupsMergeStudyAndCompletedQuizActivity() {
        dashboard();

        QueryCall recentCall = rowQueryCalls.stream()
                .filter(call -> normalize(call.sql()).contains("last_studied"))
                .findFirst()
                .orElseThrow();
        String normalizedRecentSql = normalize(recentCall.sql());
        assertThat(normalizedRecentSql).contains(
                "FROMstudy_logssl",
                "WHEREsl.user_id=?ANDsl.plan_id=?",
                "FROMquiz_sessionsqs",
                "JOINquiz_questionsqq",
                "JOINstudy_group_cardssgc",
                "WHEREqs.user_id=?ANDqs.plan_id=?",
                "qs.status='completed'",
                "WHEREg.plan_id=?",
                "LIMIT3"
        );
        assertThat(recentCall.args()).containsExactly(
                USER_ID, PLAN_ID, USER_ID, PLAN_ID, PLAN_ID
        );
    }

    private TodayDashboard dashboard() {
        return dashboard(ZoneId.of("Europe/Paris"));
    }

    private TodayDashboard dashboard(ZoneId zoneId) {
        return service.getDashboard(USER_ID, zoneId);
    }

    private TodayDashboard dashboardAndAssertDateBinding(ZoneId zoneId) {
        int firstQueryIndex = rowQueryCalls.size();
        LocalDate before = LocalDate.now(zoneId);
        TodayDashboard response = dashboard(zoneId);
        LocalDate after = LocalDate.now(zoneId);

        assertThat(response.date()).isIn(before, after);
        QueryCall streakCall = rowQueryCalls.subList(firstQueryIndex, rowQueryCalls.size()).stream()
                .filter(call -> normalize(call.sql()).contains("SELECTDISTINCTlog_date"))
                .findFirst()
                .orElseThrow();
        assertThat(normalize(streakCall.sql())).contains(
                "user_id=?",
                "plan_id=?",
                "log_date<=?"
        );
        assertThat(streakCall.args()).containsExactly(
                USER_ID, PLAN_ID, Date.valueOf(response.date())
        );
        return response;
    }

    private static boolean isDueCountSql(String sql) {
        String normalizedSql = normalize(sql);
        return normalizedSql.contains("FROMstudy_group_cardssgcJOINcard_skill_memorym")
                && normalizedSql.contains("m.due_at<=?");
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", "");
    }

    private record QueryCall(String sql, Object[] args) {
    }
}
