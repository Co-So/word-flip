package com.wordflip.repository;

import com.wordflip.domain.BookWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 词书词条仓储。
 */
public interface BookWordRepository extends JpaRepository<BookWord, Long> {

    @Query("""
            SELECT DISTINCT bw.wordKey FROM BookWord bw
            WHERE bw.bookId IN :bookIds
            """)
    List<String> findDistinctWordKeysByBookIds(@Param("bookIds") Collection<Long> bookIds);

    @Query("""
            SELECT bw FROM BookWord bw
            WHERE bw.bookId IN :bookIds AND bw.wordKey IN :wordKeys
            ORDER BY bw.bookId ASC, bw.sortOrder ASC
            """)
    List<BookWord> findByBookIdsAndWordKeys(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("wordKeys") Collection<String> wordKeys
    );

    @Query("""
            SELECT COUNT(DISTINCT bw.wordKey) FROM BookWord bw
            JOIN UserBookSelection ubs ON ubs.id.bookId = bw.bookId
            WHERE ubs.id.userId = :userId
            """)
    long countDistinctSelectedWordKeys(@Param("userId") Long userId);
}
