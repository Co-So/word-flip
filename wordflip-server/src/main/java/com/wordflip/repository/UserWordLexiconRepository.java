package com.wordflip.repository;

import com.wordflip.domain.UserWordLexicon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

/**
 * 用户学习域词典仓储。
 */
public interface UserWordLexiconRepository extends JpaRepository<UserWordLexicon, Long> {

    boolean existsByUserIdAndWordKey(Long userId, String wordKey);

    @Query("""
            SELECT uwl.wordKey FROM UserWordLexicon uwl
            WHERE uwl.userId = :userId AND uwl.wordKey IN :wordKeys
            """)
    Set<String> findExistingWordKeys(@Param("userId") Long userId, @Param("wordKeys") Collection<String> wordKeys);
}
