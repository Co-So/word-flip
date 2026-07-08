package com.wordflip.repository;

import com.wordflip.domain.GroupWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Today 仪表盘聚合 SQL（database-design §11）；限定已入组 group_words。
 */
public interface TodayQueryRepository extends JpaRepository<GroupWord, Long> {

    @Query(value = """
            SELECT COUNT(DISTINCT gw.word_key)
            FROM group_words gw
            WHERE gw.user_id = :userId
            """, nativeQuery = true)
    long countAssignedWords(@Param("userId") Long userId);

    @Query(value = """
            SELECT COUNT(*)
            FROM group_words gw
            LEFT JOIN word_mastery wm
              ON wm.user_id = gw.user_id AND wm.word_key = gw.word_key
            WHERE gw.user_id = :userId
              AND (wm.id IS NULL OR (wm.level = 'unlearned' AND wm.has_quiz_history = 0))
            """, nativeQuery = true)
    long countNewWords(@Param("userId") Long userId);

    @Query(value = """
            SELECT COUNT(*)
            FROM group_words gw
            INNER JOIN review_plans rp
              ON rp.user_id = gw.user_id AND rp.word_key = gw.word_key
            WHERE gw.user_id = :userId
              AND rp.next_review_at IS NOT NULL AND rp.next_review_at <= :today
            """, nativeQuery = true)
    long countDueReview(@Param("userId") Long userId, @Param("today") LocalDate today);

    @Query(value = """
            SELECT COUNT(DISTINCT gw.word_key)
            FROM group_words gw
            LEFT JOIN word_mastery wm
              ON wm.user_id = gw.user_id AND wm.word_key = gw.word_key
            LEFT JOIN review_plans rp
              ON rp.user_id = gw.user_id AND rp.word_key = gw.word_key
            WHERE gw.user_id = :userId
              AND (
                (rp.next_review_at IS NOT NULL AND rp.next_review_at <= :today)
                OR wm.level IN ('fuzzy', 'unknown')
              )
            """, nativeQuery = true)
    long countQuizPool(@Param("userId") Long userId, @Param("today") LocalDate today);

    @Query(value = """
            SELECT COUNT(*)
            FROM group_words gw
            INNER JOIN review_plans rp
              ON rp.user_id = gw.user_id AND rp.word_key = gw.word_key
            WHERE gw.user_id = :userId
              AND rp.stage >= 5
              AND DATEDIFF(rp.next_review_at, :today) >= 30
            """, nativeQuery = true)
    long countMastered(@Param("userId") Long userId, @Param("today") LocalDate today);

    @Query(value = """
            SELECT COUNT(*)
            FROM group_words gw
            LEFT JOIN word_mastery wm
              ON wm.user_id = gw.user_id AND wm.word_key = gw.word_key
            WHERE gw.user_id = :userId AND gw.group_id = :groupId
              AND (wm.id IS NULL OR (wm.level = 'unlearned' AND wm.has_quiz_history = 0))
            """, nativeQuery = true)
    long countNewWordsInGroup(
            @Param("userId") Long userId,
            @Param("groupId") Long groupId
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM group_words gw
            INNER JOIN review_plans rp
              ON rp.user_id = gw.user_id AND rp.word_key = gw.word_key
            WHERE gw.user_id = :userId AND gw.group_id = :groupId
              AND rp.next_review_at IS NOT NULL AND rp.next_review_at <= :today
            """, nativeQuery = true)
    long countDueReviewInGroup(
            @Param("userId") Long userId,
            @Param("groupId") Long groupId,
            @Param("today") LocalDate today
    );

    /** Today/retry 出题池 wordKey（REQ-TODAY-11）；可选 groupId 过滤 */
    @Query(value = """
            SELECT DISTINCT gw.word_key
            FROM group_words gw
            LEFT JOIN word_mastery wm
              ON wm.user_id = gw.user_id AND wm.word_key = gw.word_key
            LEFT JOIN review_plans rp
              ON rp.user_id = gw.user_id AND rp.word_key = gw.word_key
            WHERE gw.user_id = :userId
              AND (
                (rp.next_review_at IS NOT NULL AND rp.next_review_at <= :today)
                OR wm.level IN ('fuzzy', 'unknown')
              )
              AND (:groupId IS NULL OR gw.group_id = :groupId)
            ORDER BY gw.word_key
            """, nativeQuery = true)
    List<String> findQuizPoolWordKeys(
            @Param("userId") Long userId,
            @Param("today") LocalDate today,
            @Param("groupId") Long groupId
    );
}
