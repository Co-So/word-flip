package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.wordflip.dto.stats.StatsSummaryResponse;
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
 * 验证统计摘要与词书进度使用同一权威掌握口径。
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final long USER_ID = 61L;
    private static final long PLAN_ID = 601L;

    @Mock
    private JdbcTemplate jdbc;

    private StatsService service;
    private List<QueryCall> masteredCalls;

    @BeforeEach
    void setUp() {
        service = new StatsService(jdbc);
        masteredCalls = new ArrayList<>();
    }

    /**
     * 掌握数必须只统计当前计划中满足四条件且最近听写成功的当前用户卡片。
     */
    @Test
    @SuppressWarnings("unchecked")
    void summaryUsesAuthoritativeMasteryRuleAndLatestReview() {
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
                    if (sql.contains("card_skill_memory")) {
                        masteredCalls.add(new QueryCall(sql, args));
                        return 7;
                    }
                    return 0;
                });
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        StatsSummaryResponse response = service.summary(USER_ID, ZoneId.of("Asia/Shanghai"));

        assertThat(response.masteredCount()).isEqualTo(7);
        assertThat(masteredCalls).hasSize(1);
        QueryCall call = masteredCalls.getFirst();
        String normalizedSql = call.sql().replaceAll("\\s+", "");
        assertThat(normalizedSql).contains(
                "sgc.plan_id=?",
                "m.user_id=?",
                "m.skill='dictation'",
                "m.state='review'",
                "m.stability>=80",
                "m.scheduled_days>=30",
                "r.correct=TRUE"
        );
        assertThat(normalizedSql).contains(
                "r.id=(SELECTr2.idFROMreview_eventsr2",
                "r2.user_id=?",
                "r2.plan_id=sgc.plan_id",
                "r2.card_id=m.card_id",
                "r2.skill=m.skill",
                "ORDERBYr2.answered_atDESC,r2.idDESCLIMIT1"
        );
        assertThat(call.args()).containsExactly(PLAN_ID, USER_ID, USER_ID);
    }

    private record QueryCall(String sql, Object[] args) {
    }
}
