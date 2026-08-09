package com.wordflip.service;

import com.wordflip.dto.book.BookListResponse;
import com.wordflip.exception.WordflipException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 按全新 books 表读取公共词书和当前用户的私有词书。
 */
@Service
public class BookService {

    private static final String BOOK_SELECT = """
            SELECT b.id, b.name, b.source_type, b.published_card_count, b.declared_count,
                   p.id AS plan_id, p.status AS plan_status,
                   CASE WHEN us.active_plan_id=p.id THEN TRUE ELSE FALSE END AS selected,
                   COUNT(DISTINCT sgc.card_id) AS assigned_count,
                   COUNT(DISTINCT CASE
                     WHEN m.state='review'
                      AND m.stability>=80
                      AND m.scheduled_days>=30
                      AND r.correct=TRUE
                     THEN sgc.card_id
                   END) AS mastered_count
              FROM books b
              LEFT JOIN user_learning_plans p ON p.book_id=b.id AND p.user_id=?
              LEFT JOIN user_settings us ON us.user_id=?
              LEFT JOIN study_group_cards sgc ON sgc.plan_id=p.id
              LEFT JOIN card_skill_memory m
                ON m.card_id=sgc.card_id AND m.user_id=? AND m.skill='dictation'
              LEFT JOIN review_events r ON r.id=(
                SELECT r2.id
                  FROM review_events r2
                 WHERE r2.user_id=?
                   AND r2.plan_id=p.id
                   AND r2.card_id=sgc.card_id
                   AND r2.skill=m.skill
                 ORDER BY r2.answered_at DESC, r2.id DESC
                 LIMIT 1
              )
            """;

    private static final String BOOK_GROUP_BY = """
             GROUP BY b.id, b.name, b.source_type, b.published_card_count, b.declared_count,
                      p.id, p.status, us.active_plan_id
            """;

    /**
     * 当前计划优先，其次是历史计划，未创建计划的词书最后展示。
     */
    private static final String BOOK_ORDER_BY = """
             ORDER BY CASE
                        WHEN us.active_plan_id=p.id THEN 0
                        WHEN p.id IS NOT NULL THEN 1
                        ELSE 2
                      END,
                      b.id
            """;

    private final JdbcTemplate jdbc;

    public BookService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 返回已发布公共词书与当前用户自己的私有词书；selected 仅表示当前计划。
     */
    @Transactional(readOnly = true)
    public BookListResponse listBooks(Long userId) {
        List<BookListResponse.BookItem> books = jdbc.query(
                BOOK_SELECT
                        + " WHERE b.status='published' AND (b.visibility='public' OR b.owner_user_id=?)"
                        + BOOK_GROUP_BY
                        + BOOK_ORDER_BY,
                (rs, row) -> mapBook(rs),
                userId, userId, userId, userId, userId
        );
        return new BookListResponse(books);
    }

    /**
     * 获取当前用户可见的单本词书。
     */
    @Transactional(readOnly = true)
    public BookListResponse.BookItem getBook(Long userId, Long bookId) {
        List<BookListResponse.BookItem> values = jdbc.query(
                BOOK_SELECT
                        + " WHERE b.id=? AND (b.visibility='public' OR b.owner_user_id=?)"
                        + BOOK_GROUP_BY,
                (rs, row) -> mapBook(rs),
                userId, userId, userId, userId, bookId, userId
        );
        if (values.isEmpty()) {
            throw new WordflipException("NOT_FOUND", "词书不存在或不可访问");
        }
        return values.getFirst();
    }

    /**
     * 校验词书可见且已发布，可用于创建学习计划。
     */
    @Transactional(readOnly = true)
    public void requirePublishedBook(Long userId, Long bookId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM books WHERE id=? AND status='published' AND (visibility='public' OR owner_user_id=?)",
                Integer.class,
                bookId,
                userId
        );
        if (count == null || count == 0) {
            throw new WordflipException("NOT_FOUND", "词书不存在或尚未发布");
        }
    }

    private BookListResponse.BookItem mapBook(java.sql.ResultSet rs) throws java.sql.SQLException {
        String source = rs.getString("source_type");
        Long planId = rs.getObject("plan_id", Long.class);
        BookListResponse.BookProgress progress = null;
        if (planId != null) {
            int masteredCount = rs.getInt("mastered_count");
            int assignedCount = rs.getInt("assigned_count");
            int completionPercent = assignedCount == 0
                    ? 0
                    : (int) Math.round(masteredCount * 100.0 / assignedCount);
            progress = new BookListResponse.BookProgress(masteredCount, assignedCount, completionPercent);
        }
        return new BookListResponse.BookItem(
                rs.getLong("id"), rs.getString("name"), source,
                rs.getInt("published_card_count"), rs.getObject("declared_count", Integer.class),
                rs.getBoolean("selected"), "imported".equals(source),
                planId, rs.getString("plan_status"), progress
        );
    }
}
