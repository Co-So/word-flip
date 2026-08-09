package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wordflip.exception.WordflipException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 自动分组增量追加测试，覆盖当前计划隔离、策略接入与并发编排。
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceAppendTest {

    private static final long USER_ID = 7L;
    private static final long PLAN_ID = 9L;

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private LearningCardQueryService cards;

    @AfterEach
    void neverTouchesMemoryOrReviewTables() {
        assertThat(executedSql()).noneMatch(sql -> sql.contains("card_skill_memory"));
        assertThat(executedSql()).noneMatch(sql -> sql.contains("lexeme_skill_memory"));
        assertThat(executedSql()).noneMatch(sql -> sql.contains("review_events"));
    }

    @Test
    void rejectsOwnedButInactivePlanBeforeReadingCandidates() {
        stubHistoricalPlan();

        assertThatThrownBy(() -> service().appendAutoGroups(USER_ID, PLAN_ID))
                .isInstanceOf(WordflipException.class)
                .hasMessageContaining("学习计划不存在");
        assertThat(executedSql()).noneMatch(sql -> sql.contains("as card_id"));
    }

    @Test
    void emptyCandidatesReturnBeforeReadingOrCreatingGroups() {
        stubCurrentPlan(3, "book_order");
        stubCandidates(List.of());

        service().appendAutoGroups(USER_ID, PLAN_ID);

        assertThat(executedSql()).noneMatch(sql -> sql.contains("from study_groups"));
        assertThat(executedSql()).noneMatch(sql -> sql.contains("insert into study_groups"));
    }

    @Test
    void fillsLastIncompleteAutoGroupBeforeCreatingOneGroupForRemainingCards() {
        stubCurrentPlan(3, "book_order");
        stubCandidates(List.of(
                candidate(101L, 0, null),
                candidate(102L, 1, null),
                candidate(103L, 2, null)
        ));
        stubLastIncompleteGroup(3, List.of(group(41L, 2, 0)));
        stubNewGroup(1, 42L);

        service().appendAutoGroups(USER_ID, PLAN_ID);

        verify(jdbc).queryForList(
                argThat((String sql) -> sql != null
                        && sql.contains("HAVING COUNT(sgc.id) < ?")
                        && sql.contains("ORDER BY g.sort_order DESC, g.id DESC")),
                eq(PLAN_ID), eq(3)
        );
        verifyMemberInsert(41L, 101L, 2);
        verifyGroupInsert(1);
        verifyMemberInsert(42L, 102L, 0);
        verifyMemberInsert(42L, 103L, 1);
    }

    @Test
    void frequencyMapsNumericAndNullRanksIntoInsertionOrder() {
        stubCurrentPlan(4, "frequency");
        stubCandidates(List.of(
                candidate(201L, 3, null),
                candidate(202L, 1, 20L),
                candidate(203L, 2, null),
                candidate(204L, 0, BigInteger.valueOf(5))
        ));
        stubLastIncompleteGroup(4, List.of());
        stubNewGroup(0, 51L);

        service().appendAutoGroups(USER_ID, PLAN_ID);

        assertThat(insertedCardIds()).containsExactly(204L, 202L, 203L, 201L);
        assertThat(executedSql()).anyMatch(sql -> sql.contains("select last_insert_id()"));
    }

    @Test
    void randomStrategyProducesStableIntegratedInsertionOrder() {
        stubCurrentPlan(6, "random");
        stubCandidates(List.of(
                candidate(106L, 5, null),
                candidate(105L, 4, null),
                candidate(104L, 3, null),
                candidate(103L, 2, null),
                candidate(102L, 1, null),
                candidate(101L, 0, null)
        ));
        stubLastIncompleteGroup(6, List.of());
        stubNewGroup(0, 61L);

        GroupService service = service();
        service.appendAutoGroups(USER_ID, PLAN_ID);
        List<Long> first = insertedCardIds();
        clearInvocations(jdbc);

        service.appendAutoGroups(USER_ID, PLAN_ID);
        List<Long> second = insertedCardIds();

        assertThat(first).containsExactlyElementsOf(second);
        assertThat(first).isNotEqualTo(List.of(101L, 102L, 103L, 104L, 105L, 106L));
        assertThat(executedSql()).anyMatch(sql -> sql.contains("select last_insert_id()"));
    }

    @Test
    void locksCurrentPlanBeforeAllocatingOrderAndUsesConnectionGeneratedId() {
        stubCurrentPlan(3, "book_order");
        stubCandidates(List.of(candidate(301L, 0, null)));
        stubLastIncompleteGroup(3, List.of());
        stubNewGroup(0, 71L);

        service().appendAutoGroups(USER_ID, PLAN_ID);

        List<String> sql = executedSql();
        int lockIndex = firstSqlIndex(sql, "for update");
        int maxOrderIndex = firstSqlIndex(sql, "coalesce(max(sort_order)");
        int insertGroupIndex = firstSqlIndex(sql, "insert into study_groups");
        int generatedIdIndex = firstSqlIndex(sql, "select last_insert_id()");
        assertThat(sql.stream().filter(statement -> statement.contains("for update")))
                .hasSize(1);
        assertThat(lockIndex).isGreaterThanOrEqualTo(0).isLessThan(maxOrderIndex);
        assertThat(maxOrderIndex).isLessThan(insertGroupIndex);
        assertThat(insertGroupIndex).isLessThan(generatedIdIndex);
        assertThat(sql).noneMatch(statement -> statement.contains(
                "select id from study_groups where plan_id=? and sort_order=?"
        ));
        verifyMemberInsert(71L, 301L, 0);
    }

    @Test
    void batchesLargeMemberAssignmentsInsteadOfUpdatingEachCardSeparately() {
        stubCurrentPlan(100, "book_order");
        stubCandidates(IntStream.range(0, 100)
                .mapToObj(index -> candidate(1_000L + index, index, null))
                .toList());
        stubLastIncompleteGroup(100, List.of());
        stubNewGroup(0, 81L);

        service().appendAutoGroups(USER_ID, PLAN_ID);

        verify(jdbc).batchUpdate(
                argThat((String sql) -> sql.startsWith("INSERT INTO study_group_cards")),
                anyList()
        );
        verify(jdbc, never()).update(
                argThat((String sql) -> sql.startsWith("INSERT INTO study_group_cards")),
                argThat((Object[] args) -> true)
        );
    }

    private GroupService service() {
        return new GroupService(jdbc, cards);
    }

    private void stubCurrentPlan(int groupSize, String strategy) {
        when(jdbc.queryForList(
                argThat((String sql) -> isCurrentPlanLock(sql)),
                eq(USER_ID), eq(PLAN_ID)
        )).thenReturn(List.of(Map.of(
                "plan_id", PLAN_ID,
                "group_size", groupSize,
                "group_strategy", strategy
        )));
    }

    private void stubHistoricalPlan() {
        when(jdbc.queryForList(
                argThat((String sql) -> isCurrentPlanLock(sql)),
                eq(USER_ID), eq(PLAN_ID)
        )).thenReturn(List.of());
    }

    private boolean isCurrentPlanLock(String sql) {
        return sql != null
                && sql.contains("JOIN user_learning_plans")
                && sql.contains("active_plan_id")
                && sql.contains("FOR UPDATE");
    }

    private void stubCandidates(List<Map<String, Object>> candidates) {
        when(jdbc.queryForList(
                argThat((String sql) -> sql != null && sql.contains("AS card_id")
                        && sql.contains("JSON_EXTRACT(bi.metadata_json, '$.frequencyRank')")),
                eq(PLAN_ID)
        )).thenReturn(candidates);
    }

    private void stubLastIncompleteGroup(int groupSize, List<Map<String, Object>> groups) {
        when(jdbc.queryForList(
                argThat((String sql) -> sql != null
                        && sql.contains("FROM study_groups g")
                        && sql.contains("HAVING COUNT(sgc.id) < ?")),
                eq(PLAN_ID), eq(groupSize)
        )).thenReturn(groups);
    }

    private void stubNewGroup(int sortOrder, long groupId) {
        when(jdbc.queryForObject(
                argThat((String sql) -> sql != null
                        && sql.contains("COALESCE(MAX(sort_order), -1)+1")),
                eq(Integer.class), eq(PLAN_ID)
        )).thenReturn(sortOrder);
        when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(groupId);
    }

    private void verifyGroupInsert(int sortOrder) {
        verify(jdbc).update(
                argThat((String sql) -> sql.startsWith("INSERT INTO study_groups")),
                eq(PLAN_ID), eq("第 " + (sortOrder + 1) + " 组"), eq(sortOrder)
        );
    }

    private void verifyMemberInsert(long groupId, long cardId, int sortOrder) {
        assertThat(insertedRows()).anySatisfy(row -> assertThat(row).containsExactly(
                groupId, PLAN_ID, cardId, sortOrder
        ));
    }

    private List<Long> insertedCardIds() {
        return insertedRows().stream()
                .map(row -> ((Number) row[2]).longValue())
                .toList();
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

    private int firstSqlIndex(List<String> sql, String fragment) {
        for (int index = 0; index < sql.size(); index++) {
            if (sql.get(index).contains(fragment)) {
                return index;
            }
        }
        return -1;
    }

    private Map<String, Object> candidate(long cardId, int bookOrder, Number frequencyRank) {
        Map<String, Object> row = new HashMap<>();
        row.put("card_id", cardId);
        row.put("book_order", bookOrder);
        row.put("frequency_rank", frequencyRank);
        return row;
    }

    private Map<String, Object> group(long groupId, int groupSize, int sortOrder) {
        return Map.of(
                "group_id", groupId,
                "group_size", groupSize,
                "sort_order", sortOrder
        );
    }
}
