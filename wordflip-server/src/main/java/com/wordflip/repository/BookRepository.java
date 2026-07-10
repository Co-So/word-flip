package com.wordflip.repository;

import com.wordflip.domain.Book;
import com.wordflip.domain.BookSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 词书仓储。
 */
public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("""
            SELECT b FROM Book b
            WHERE b.source = com.wordflip.domain.BookSource.builtin
               OR b.userId = :userId
            ORDER BY b.source ASC, b.id ASC
            """)
    List<Book> findVisibleBooks(@Param("userId") Long userId);

    List<Book> findBySourceAndUserId(BookSource source, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);
}
