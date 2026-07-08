package com.wordflip.repository;

import com.wordflip.domain.GroupSource;
import com.wordflip.domain.GroupWord;
import com.wordflip.domain.StudyGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

/**
 * 分组单词关联仓储。
 */
public interface GroupWordRepository extends JpaRepository<GroupWord, Long> {

    @Query("SELECT gw.wordKey FROM GroupWord gw WHERE gw.userId = :userId")
    Set<String> findWordKeysByUserId(@Param("userId") Long userId);

    List<GroupWord> findByGroupIdOrderBySortOrderAsc(Long groupId);

    Page<GroupWord> findByGroupIdOrderBySortOrderAsc(Long groupId, Pageable pageable);

    long countByGroupId(Long groupId);

    /** custom 组内 wordKey，重新分组时保留（REQ-BOOK-26） */
    @Query("""
            SELECT gw.wordKey FROM GroupWord gw
            JOIN StudyGroup g ON g.id = gw.groupId
            WHERE gw.userId = :userId AND g.source = :source
            """)
    Set<String> findWordKeysByUserIdAndGroupSource(
            @Param("userId") Long userId,
            @Param("source") GroupSource source
    );
}
