package com.wordflip.repository;

import com.wordflip.domain.GroupSource;
import com.wordflip.domain.StudyGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 学习分组仓储。
 */
public interface GroupRepository extends JpaRepository<StudyGroup, Long> {

    int countByUserIdAndSource(Long userId, GroupSource source);

    @Query("SELECT COALESCE(MAX(g.sortOrder), 0) FROM StudyGroup g WHERE g.userId = :userId")
    Optional<Integer> findMaxSortOrderByUserId(@Param("userId") Long userId);
}
