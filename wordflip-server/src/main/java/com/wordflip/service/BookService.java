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
                   CASE WHEN us.active_plan_id=p.id THEN TRUE ELSE FALSE END AS selected
              FROM books b
              LEFT JOIN user_learning_plans p ON p.book_id=b.id AND p.user_id=?
              LEFT JOIN user_settings us ON us.user_id=?
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
                BOOK_SELECT + " WHERE b.status='published' AND (b.visibility='public' OR b.owner_user_id=?) ORDER BY b.id",
                (rs, row) -> mapBook(rs),
                userId, userId, userId
        );
        return new BookListResponse(books);
    }

    /**
     * 获取当前用户可见的单本词书。
     */
    @Transactional(readOnly = true)
    public BookListResponse.BookItem getBook(Long userId, Long bookId) {
        List<BookListResponse.BookItem> values = jdbc.query(
                BOOK_SELECT + " WHERE b.id=? AND (b.visibility='public' OR b.owner_user_id=?)",
                (rs, row) -> mapBook(rs),
                userId, userId, bookId, userId
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
        return new BookListResponse.BookItem(
                rs.getLong("id"), rs.getString("name"), source,
                rs.getInt("published_card_count"), rs.getObject("declared_count", Integer.class),
                rs.getBoolean("selected"), "imported".equals(source)
        );
    }
}
