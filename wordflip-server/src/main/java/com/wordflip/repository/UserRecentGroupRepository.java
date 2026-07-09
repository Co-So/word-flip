package com.wordflip.repository;

import com.wordflip.domain.UserRecentGroup;
import com.wordflip.domain.UserRecentGroupId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 最近学习分组仓储。
 */
public interface UserRecentGroupRepository extends JpaRepository<UserRecentGroup, UserRecentGroupId> {

    @Query("""
            SELECT r FROM UserRecentGroup r
            WHERE r.userId = :userId
            ORDER BY r.lastStudiedAt DESC
            """)
    List<UserRecentGroup> findRecentByUserId(@Param("userId") Long userId);
}
