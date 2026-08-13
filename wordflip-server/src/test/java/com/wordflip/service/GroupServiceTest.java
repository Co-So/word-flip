package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wordflip.dto.group.CreateCustomGroupRequest;
import com.wordflip.dto.group.GroupDetail;
import com.wordflip.dto.group.GroupStats;
import com.wordflip.exception.WordflipException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource({
            "name, ORDER BY g.name, g.id",
            "createdAt, ORDER BY g.created_at DESC, g.id DESC"
    })
    void legalSortUsesFixedSqlOrder(String sort, String expectedOrder) {
        stubCurrentPlan();
        when(jdbc.queryForList(
                argThat((String sql) -> sql != null && sql.startsWith("SELECT g.id FROM study_groups")),
                eq(Long.class), eq(PLAN_ID)
        )).thenReturn(List.of());

        service().listGroups(USER_ID, null, sort);

        assertThat(executedSql()).anySatisfy(sql -> assertThat(sql)
                .contains(expectedOrder.toLowerCase(Locale.ROOT)));
    }

    @Test
    void allModeUsesFixedFetchWindowAndUnpagedMetadata() {
        stubCurrentPlan();
        when(jdbc.queryForObject(
                argThat((String sql) -> sql != null && sql.startsWith("SELECT COUNT(*)")),
                eq(Long.class), eq(PLAN_ID), eq("%"), eq("%")
        )).thenReturn(7_000L);
        when(jdbc.queryForList(
                argThat((String sql) -> sql != null && sql.startsWith("SELECT c.id")),
                eq(Long.class), eq(PLAN_ID), eq("%"), eq("%"), eq(5_000), eq(0)
        )).thenReturn(List.of());

        var response = service().listUnassignedCards(USER_ID, true, null, 3, 50);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(5_000);
        assertThat(response.totalElements()).isEqualTo(7_000);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.cards()).isEmpty();
        verify(jdbc).queryForList(
                argThat((String sql) -> sql != null && sql.startsWith("SELECT c.id")),
                eq(Long.class), eq(PLAN_ID), eq("%"), eq("%"), eq(5_000), eq(0)
        );
    }

    @Test
    void emptyCustomGroupIsRejectedBeforeAnySqlForDirectServiceCalls() {
        GroupService service = service();

        assertValidationError(() -> service.createCustomGroup(USER_ID, null));
        assertValidationError(() -> service.createCustomGroup(
                USER_ID, new CreateCustomGroupRequest(null, "空组")
        ));
        assertValidationError(() -> service.createCustomGroup(
                USER_ID, new CreateCustomGroupRequest(List.of(), "空组")
        ));

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

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, not_started, 0.0",
            "4, 0, 0, not_started, 0.0",
            "4, 2, 1, learning, 0.25",
            "4, 4, 4, completed, 1.0"
    })
    void mapsReviewedAndMasteredCountsToStatusAndProgress(
            int total,
            int reviewed,
            int mastered,
            String expectedStatus,
            float expectedProgress
    ) throws SQLException {
        stubOwnedGroup();
        stubMappedGroup(total, reviewed, mastered);

        GroupDetail detail = service().getGroup(USER_ID, GROUP_ID);

        assertThat(detail.status()).isEqualTo(expectedStatus);
        assertThat(detail.progress()).isEqualTo(expectedProgress);
        assertThat(detail.stats().total()).isEqualTo(total);
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
        verify(jdbc, times(1)).batchUpdate(
                argThat((String sql) -> sql.startsWith("INSERT INTO study_group_cards")),
                anyList()
        );
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
    private void stubMappedGroup(int total, int reviewed, int mastered) throws SQLException {
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(GROUP_ID);
        when(resultSet.getString("name")).thenReturn("重点");
        when(resultSet.getString("source")).thenReturn("custom");
        when(resultSet.getTimestamp("created_at"))
                .thenReturn(Timestamp.from(Instant.parse("2026-08-13T00:00:00Z")));
        when(resultSet.getInt("total")).thenReturn(total);
        when(resultSet.getInt("reviewed")).thenReturn(reviewed);
        when(resultSet.getInt("mastered")).thenReturn(mastered);
        when(jdbc.queryForObject(
                argThat((String sql) -> sql != null && sql.contains("COUNT(sgc.id) AS total")),
                any(RowMapper.class), eq(USER_ID), eq(USER_ID), eq(GROUP_ID)
        )).thenAnswer(invocation -> {
            RowMapper<GroupDetail> mapper = invocation.getArgument(1);
            return mapper.mapRow(resultSet, 0);
        });
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
