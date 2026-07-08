package com.wordflip.repository;

import com.wordflip.domain.QuizSession;
import com.wordflip.domain.QuizSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 测验会话仓储。
 */
public interface QuizSessionRepository extends JpaRepository<QuizSession, String> {

    Optional<QuizSession> findByIdAndUserId(String id, Long userId);

    Optional<QuizSession> findByIdAndUserIdAndStatus(String id, Long userId, QuizSessionStatus status);
}
