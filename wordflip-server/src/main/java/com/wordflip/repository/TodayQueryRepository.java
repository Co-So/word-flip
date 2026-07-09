package com.wordflip.repository;

import com.wordflip.domain.GroupWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Today 仪表盘聚合 SQL；进度以 word_skill_progress 为准（双 skill）。
 * <ul>
 *   <li>到期：任一 skill 到期，按 wordKey 去重</li>
 *   <li>新词：两 skill 均无测验史（含无进度行）</li>
 *   <li>已掌握：两 skill 均 S≥80 且建议间隔≥30 天</li>
 * </ul>
 */
public interface TodayQueryRepository extends JpaRepository<GroupWord, Long> {

    @Query(value = """
            SELECT COUNT(DISTINCT gw.word_key)
            FROM group_words gw
            WHERE gw.user_id = :userId
            """, nativeQuery = true)
    long countAssignedWords(@Param("userId") Long userId);

    /** 新词：dictation 与 choice 均无测验史（或无进度行） */
    @Query(value = """
            SELECT COUNT(*)
            FROM group_words gw
            LEFT JOIN word_skill_progress d
              ON d.user_id = gw.user_id AND d.word_key = gw.word_key AND d.skill = 'dictation'
            LEFT JOIN word_skill_progress c
              ON c.user_id = gw.user_id AND c.word_key = gw.word_key AND c.skill = 'choice'
            WHERE gw.user_id = :userId
              AND COALESCE(d.has_quiz_history, 0) = 0
              AND COALESCE(c.has_quiz_history, 0) = 0
            """, nativeQuery = true)
    long countNewWords(@Param("userId") Long userId);

    /** 到期：任一 skill 的 next_review_at <= today，按 wordKey 去重 */
    @Query(value = """
            SELECT COUNT(DISTINCT gw.word_key)
            FROM group_words gw
            INNER JOIN word_skill_progress wsp
              ON wsp.user_id = gw.user_id AND wsp.word_key = gw.word_key
            WHERE gw.user_id = :userId
              AND wsp.next_review_at IS NOT NULL AND wsp.next_review_at <= :today
            """, nativeQuery = true)
    long countDueReview(@Param("userId") Long userId, @Param("today") LocalDate today);

    /** 测验池：到期 ∪ fuzzy/unknown（任一 skill），按 wordKey 去重 */
    @Query(value = """
            SELECT COUNT(DISTINCT gw.word_key)
            FROM group_words gw
            INNER JOIN word_skill_progress wsp
              ON wsp.user_id = gw.user_id AND wsp.word_key = gw.word_key
            WHERE gw.user_id = :userId
              AND (
                (wsp.next_review_at IS NOT NULL AND wsp.next_review_at <= :today)
                OR wsp.level IN ('fuzzy', 'unknown')
              )
            """, nativeQuery = true)
    long countQuizPool(@Param("userId") Long userId, @Param("today") LocalDate today);

    /**
     * 已掌握：两 skill 均存在、S≥80、有测验史，且各自建议间隔 ≥30 天。
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM group_words gw
            INNER JOIN word_skill_progress d
              ON d.user_id = gw.user_id AND d.word_key = gw.word_key AND d.skill = 'dictation'
            INNER JOIN word_skill_progress c
              ON c.user_id = gw.user_id AND c.word_key = gw.word_key AND c.skill = 'choice'
            WHERE gw.user_id = :userId
              AND d.stability >= 80 AND c.stability >= 80
              AND d.has_quiz_history = 1 AND c.has_quiz_history = 1
              AND d.next_review_at IS NOT NULL AND c.next_review_at IS NOT NULL
              AND DATEDIFF(d.next_review_at, :today) >= 30
              AND DATEDIFF(c.next_review_at, :today) >= 30
            """, nativeQuery = true)
    long countMastered(@Param("userId") Long userId, @Param("today") LocalDate today);

    @Query(value = """
            SELECT COUNT(*)
            FROM group_words gw
            LEFT JOIN word_skill_progress d
              ON d.user_id = gw.user_id AND d.word_key = gw.word_key AND d.skill = 'dictation'
            LEFT JOIN word_skill_progress c
              ON c.user_id = gw.user_id AND c.word_key = gw.word_key AND c.skill = 'choice'
            WHERE gw.user_id = :userId AND gw.group_id = :groupId
              AND COALESCE(d.has_quiz_history, 0) = 0
              AND COALESCE(c.has_quiz_history, 0) = 0
            """, nativeQuery = true)
    long countNewWordsInGroup(
            @Param("userId") Long userId,
            @Param("groupId") Long groupId
    );

    @Query(value = """
            SELECT COUNT(DISTINCT gw.word_key)
            FROM group_words gw
            INNER JOIN word_skill_progress wsp
              ON wsp.user_id = gw.user_id AND wsp.word_key = gw.word_key
            WHERE gw.user_id = :userId AND gw.group_id = :groupId
              AND wsp.next_review_at IS NOT NULL AND wsp.next_review_at <= :today
            """, nativeQuery = true)
    long countDueReviewInGroup(
            @Param("userId") Long userId,
            @Param("groupId") Long groupId,
            @Param("today") LocalDate today
    );

    /** Today/retry 出题池 wordKey；可选 groupId 过滤 */
    @Query(value = """
            SELECT DISTINCT gw.word_key
            FROM group_words gw
            INNER JOIN word_skill_progress wsp
              ON wsp.user_id = gw.user_id AND wsp.word_key = gw.word_key
            WHERE gw.user_id = :userId
              AND (
                (wsp.next_review_at IS NOT NULL AND wsp.next_review_at <= :today)
                OR wsp.level IN ('fuzzy', 'unknown')
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
