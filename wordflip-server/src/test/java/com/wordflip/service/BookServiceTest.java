package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wordflip.dto.book.BookListResponse;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 验证词书列表把认证用户的计划状态和听写进度映射到响应。
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    private static final long USER_ID = 41L;

    @Mock
    private JdbcTemplate jdbc;

    private BookService service;

    @BeforeEach
    void setUp() {
        service = new BookService(jdbc);
    }

    /**
     * 有计划时必须返回计划身份、状态、当前选中标记和按卡去重的真实进度。
     */
    @Test
    void returnsPlanStateAndProgressForPlannedBook() throws Exception {
        ResultSet row = bookRow(101L, "核心词书", "builtin", true);
        when(row.getObject("plan_id", Long.class)).thenReturn(501L);
        when(row.getString("plan_status")).thenReturn("active");
        when(row.getInt("mastered_count")).thenReturn(3);
        when(row.getInt("assigned_count")).thenReturn(5);
        stubQuery(row);

        BookListResponse.BookItem book = service.listBooks(USER_ID).books().getFirst();

        assertThat(book.planId()).isEqualTo(501L);
        assertThat(book.planStatus()).isEqualTo("active");
        assertThat(book.selected()).isTrue();
        assertThat(book.progress()).isEqualTo(new BookListResponse.BookProgress(3, 5, 60));
    }

    /**
     * 非整除完成率必须四舍五入，不能直接截断小数部分。
     */
    @Test
    void roundsNonIntegralCompletionPercent() throws Exception {
        ResultSet row = bookRow(106L, "舍入词书", "builtin", true);
        when(row.getObject("plan_id", Long.class)).thenReturn(506L);
        when(row.getString("plan_status")).thenReturn("active");
        when(row.getInt("mastered_count")).thenReturn(2);
        when(row.getInt("assigned_count")).thenReturn(3);
        stubQuery(row);

        BookListResponse.BookItem book = service.listBooks(USER_ID).books().getFirst();

        assertThat(book.progress()).isEqualTo(new BookListResponse.BookProgress(2, 3, 67));
    }

    /**
     * 无计划的词书不能伪造计划状态或 0 进度。
     */
    @Test
    void returnsNullPlanFieldsAndProgressForUnplannedBook() throws Exception {
        ResultSet row = bookRow(102L, "未开始词书", "builtin", false);
        when(row.getObject("plan_id", Long.class)).thenReturn(null);
        stubQuery(row);

        BookListResponse.BookItem book = service.listBooks(USER_ID).books().getFirst();

        assertThat(book.planId()).isNull();
        assertThat(book.planStatus()).isNull();
        assertThat(book.selected()).isFalse();
        assertThat(book.progress()).isNull();
    }

    /**
     * 有计划但尚未分配卡片时完成率必须稳定为零，不能发生除零。
     */
    @Test
    void returnsZeroProgressForPlanWithoutAssignedCards() throws Exception {
        ResultSet row = bookRow(103L, "空计划词书", "builtin", false);
        when(row.getObject("plan_id", Long.class)).thenReturn(503L);
        when(row.getString("plan_status")).thenReturn("paused");
        when(row.getInt("mastered_count")).thenReturn(0);
        when(row.getInt("assigned_count")).thenReturn(0);
        stubQuery(row);

        BookListResponse.BookItem book = service.listBooks(USER_ID).books().getFirst();

        assertThat(book.progress()).isEqualTo(new BookListResponse.BookProgress(0, 0, 0));
    }

    /**
     * 列表 SQL 必须同时隔离计划和记忆，并只统计听写轨道。
     */
    @Test
    void isolatesPlanAndUsesLatestSuccessfulDictationReviewInListQuery() throws Exception {
        ResultSet row = bookRow(104L, "隔离词书", "builtin", false);
        when(row.getObject("plan_id", Long.class)).thenReturn(null);
        stubQuery(row);

        service.listBooks(USER_ID);

        QueryCall call = capturedQuery();
        String normalizedSql = call.sql().replaceAll("\\s+", "");
        assertThat(normalizedSql).contains(
                "p.user_id=?",
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
                "r2.plan_id=p.id",
                "r2.card_id=sgc.card_id",
                "r2.skill=m.skill",
                "ORDERBYr2.answered_atDESC,r2.idDESCLIMIT1"
        );
        assertThat(normalizedSql).contains("COUNT(DISTINCTsgc.card_id)");
        assertThat(call.args()).containsExactly(USER_ID, USER_ID, USER_ID, USER_ID, USER_ID);
    }

    /**
     * 单本查询也必须给计划和记忆隔离条件传入当前认证用户。
     */
    @Test
    void passesAuthenticatedUserToBothIsolationConditionsInDetailQuery() throws Exception {
        ResultSet row = bookRow(105L, "详情词书", "builtin", false);
        when(row.getObject("plan_id", Long.class)).thenReturn(null);
        stubQuery(row);

        service.getBook(USER_ID, 105L);

        QueryCall call = capturedQuery();
        String normalizedSql = call.sql().replaceAll("\\s+", "");
        assertThat(normalizedSql).contains(
                "p.user_id=?",
                "m.user_id=?",
                "m.skill='dictation'",
                "r2.user_id=?"
        );
        assertThat(call.args()).containsExactly(USER_ID, USER_ID, USER_ID, USER_ID, 105L, USER_ID);
    }

    @SuppressWarnings("unchecked")
    private void stubQuery(ResultSet row) {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<BookListResponse.BookItem> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });
    }

    private ResultSet bookRow(long id, String name, String source, boolean selected) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(id);
        when(row.getString("name")).thenReturn(name);
        when(row.getString("source_type")).thenReturn(source);
        when(row.getInt("published_card_count")).thenReturn(5);
        when(row.getObject("declared_count", Integer.class)).thenReturn(5);
        when(row.getBoolean("selected")).thenReturn(selected);
        return row;
    }

    @SuppressWarnings("unchecked")
    private QueryCall capturedQuery() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        return new QueryCall(sql.getValue(), args.getValue());
    }

    private record QueryCall(String sql, Object[] args) {
    }
}
