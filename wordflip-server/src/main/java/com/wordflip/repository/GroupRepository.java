package com.wordflip.repository;

import com.wordflip.domain.GroupSource;
import com.wordflip.domain.StudyGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 学习分组仓储。
 */
public interface GroupRepository extends JpaRepository<StudyGroup, Long> {

    int countByUserIdAndSource(Long userId, GroupSource source);

    @Query("SELECT COALESCE(MAX(g.sortOrder), 0) FROM StudyGroup g WHERE g.userId = :userId")
    Optional<Integer> findMaxSortOrderByUserId(@Param("userId") Long userId);

    List<StudyGroup> findByUserIdOrderByCreatedAtAsc(Long userId);

    List<StudyGroup> findByUserIdOrderByNameAsc(Long userId);

    List<StudyGroup> findByUserIdAndSourceOrderByCreatedAtAsc(Long userId, GroupSource source);

    List<StudyGroup> findByUserIdAndSourceOrderByNameAsc(Long userId, GroupSource source);

    Optional<StudyGroup> findByIdAndUserId(Long id, Long userId);

    /** 取 sort_order 最大的 auto 分组，用于 REQ-BOOK-25 补齐未满组 */
    Optional<StudyGroup> findTopByUserIdAndSourceOrderBySortOrderDesc(Long userId, GroupSource source);

    /** REQ-BOOK-26：删除全部 auto 分组（group_words 随 FK CASCADE 删除） */
    @Modifying
    @Query("DELETE FROM StudyGroup g WHERE g.userId = :userId AND g.source = :source")
    void deleteByUserIdAndSource(@Param("userId") Long userId, @Param("source") GroupSource source);
}
