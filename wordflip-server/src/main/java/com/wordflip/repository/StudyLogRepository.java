package com.wordflip.repository;

import com.wordflip.domain.StudyLog;
import com.wordflip.domain.StudyLogId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 每日学习日志仓储。
 */
public interface StudyLogRepository extends JpaRepository<StudyLog, StudyLogId> {

    Optional<StudyLog> findByUserIdAndLogDate(Long userId, LocalDate logDate);

    /** 活跃日列表：activity_score>0 或 quiz_answered>0，供 streak 计算 */
    @Query("""
            SELECT sl.logDate FROM StudyLog sl
            WHERE sl.userId = :userId
              AND (sl.activityScore > 0 OR sl.quizAnswered > 0)
            ORDER BY sl.logDate DESC
            """)
    List<LocalDate> findActiveLogDates(@Param("userId") Long userId);
}
