package com.wordflip.repository;

import com.wordflip.domain.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 测验题目快照仓储。
 */
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findBySessionIdOrderByQuestionIndexAsc(String sessionId);

    Optional<QuizQuestion> findBySessionIdAndQuestionIndex(String sessionId, int questionIndex);
}
