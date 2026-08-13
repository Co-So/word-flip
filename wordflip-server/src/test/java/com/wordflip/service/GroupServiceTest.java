package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wordflip.dto.group.CreateCustomGroupRequest;
import com.wordflip.dto.group.GroupDetail;
import com.wordflip.dto.group.GroupStats;
import com.wordflip.exception.WordflipException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 当前计划分组的查询口径、参数校验与成员去重测试。
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    private static final long USER_ID = 7L;
    private static final long PLAN_ID = 9L;
    private static final long GROUP_ID = 11L;

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private LearningCardQueryService cards;

    @Test
    void rejectsInvalidListAndPageOptionsBeforeAnySql() {
        GroupService service = service();

        assertValidationError(() -> service.listGroups(USER_ID, "other", "createdAt"));
        assertValidationError(() -> service.listGroups(USER_ID, null, "sql"));
        assertValidationError(() -> service.listGroupCards(USER_ID, GROUP_ID, 0, 20));
        assertValidationError(() -> service.listUnassignedCards(USER_ID, false, null, 1, 101));

        verifyNoInteractions(jdbc, cards);
    }

    @Test
    void allModeStillValidatesCallerPageAndSizeBeforeUsingInternalCap() {
        GroupService service = service();

        assertValidationError(() -> service.listUnassignedCards(USER_ID, true, null, 0, 20));
        assertValidationError(() -> service.listUnassignedCards(USER_ID, true, null, 1, 101));

        verifyNoInteractions(jdbc, cards);
    }

    @Test
    void groupProgressUsesAuthoritativeMasteryWhileHeatKeepsStabilityBuckets() {
        stubOwnedGroup();
        stubGroupDetail();

        service().getGroup(USER_ID, GROUP_ID);

        String sql = executedSql().stream()
                .filter(statement -> statement.contains("count(sgc.id) as total"))
                .findFirst()
                .orElseThrow();
        assertThat(sql).contains(
                "m.skill='dictation'",
                "m.state='review'",
                "m.stability>=80",
                "m.scheduled_days>=30",
                "r.correct=true"
        );
        assertThat(sql.replaceAll("\\s+", "")).contains(
                "r.id=(selectr2.idfromreview_eventsr2",
                "r2.user_id=?",
                "r2.plan_id=g.plan_id",
                "r2.card_id=m.card_id",
                "r2.skill=m.skill",
                "orderbyr2.answered_atdesc,r2.iddesclimit1"
        );
        assertThat(sql).contains(
                "m.stability<3",
                "m.stability<15",
                "m.stability<30",
                "m.stability>=30"
        );
    }

    @Test
    void ownedGroupLookupRequiresCurrentPlanAndUsesUniformNotFound() {
        when(jdbc.queryForObject(
                argThat((String sql) -> sql != null
                        && sql.contains("SELECT COUNT(*) FROM study_groups")),
                eq(Integer.class), eq(GROUP_ID), eq(USER_ID)
        )).thenReturn(0);

        assertThatThrownBy(() -> service().getGroup(USER_ID, GROUP_ID))
                .isInstanceOfSatisfying(WordflipException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("NOT_FOUND");
                    assertThat(exception.getMessage()).isEqualTo("当前学习计划中没有该分组");
                });

        String sql = executedSql().getFirst();
        assertThat(sql).contains(
                "join user_learning_plans p on p.id=g.plan_id",
                "join user_settings us on us.active_plan_id=p.id and us.user_id=p.user_id",
                "where g.id=? and p.user_id=?"
        );
    }

    @Test
    void duplicateCardIdsAreInsertedOnceThroughOneBatch() {
        stubCurrentPlan();
        when(jdbc.queryForObject(
                argThat((String sql) -> sql != null
                        && sql.contains("JOIN book_items") && sql.contains("NOT EXISTS")),
                eq(Integer.class), eq(PLAN_ID), anyLong()
        )).thenReturn(1);
        when(jdbc.queryForObject(
                argThat((String sql) -> sql != null
                        && sql.contains("COALESCE(MAX(sort_order), -1)+1")),
                eq(Integer.class), eq(PLAN_ID)
        )).thenReturn(0);
        when(jdbc.queryForObject(
                argThat((String sql) -> sql != null && sql.contains("SELECT id FROM study_groups")),
                eq(Long.class), eq(PLAN_ID), eq(0)
        )).thenReturn(GROUP_ID);
        stubOwnedGroup();
        stubGroupDetail();

        service().createCustomGroup(
                USER_ID,
                new CreateCustomGroupRequest(List.of(101L, 102L, 101L), "重点")
        );

        List<Object[]> inserted = insertedRows();
        assertThat(inserted).hasSize(2);
        assertThat(inserted).extracting(row -> ((Number) row[2]).longValue())
                .containsExactly(101L, 102L);
        assertThat(inserted).extracting(row -> ((Number) row[3]).intValue())
                .containsExactly(0, 1);
    }

    private GroupService service() {
        return new GroupService(jdbc, cards);
    }

    private void assertValidationError(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(WordflipException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("VALIDATION_ERROR"));
    }

    private void stubCurrentPlan() {
        when(jdbc.queryForList(
                argThat((String sql) -> sql != null
                        && sql.contains("SELECT active_plan_id FROM user_settings")),
                eq(Long.class), eq(USER_ID)
        )).thenReturn(List.of(PLAN_ID));
    }

    private void stubOwnedGroup() {
        when(jdbc.queryForObject(
                argThat((String sql) -> sql != null
                        && sql.contains("SELECT COUNT(*) FROM study_groups")),
                eq(Integer.class), eq(GROUP_ID), eq(USER_ID)
        )).thenReturn(1);
    }

    @SuppressWarnings("unchecked")
    private void stubGroupDetail() {
        GroupDetail detail = new GroupDetail(
                GROUP_ID,
                "重点",
                "custom",
                "learning",
                Instant.parse("2026-08-13T00:00:00Z"),
                new GroupStats(1, 1, 0, 0, 0, 2),
                0.5F
        );
        when(jdbc.queryForObject(
                argThat((String sql) -> sql != null && sql.contains("COUNT(sgc.id) AS total")),
                any(RowMapper.class), eq(USER_ID), eq(USER_ID), eq(GROUP_ID)
        )).thenReturn(detail);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> insertedRows() {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("batchUpdate"))
                .filter(invocation -> invocation.getArgument(0, String.class)
                        .startsWith("INSERT INTO study_group_cards"))
                .flatMap(invocation -> ((List<Object[]>) invocation.getArgument(1)).stream())
                .toList();
    }

    private List<String> executedSql() {
        return mockingDetails(jdbc).getInvocations().stream()
                .map(invocation -> invocation.getArgument(0))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(sql -> sql.toLowerCase(Locale.ROOT))
                .toList();
    }
}
