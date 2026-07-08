package com.wordflip.repository;

import com.wordflip.domain.QuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 测验作答仓储；连续答错判定查最近一条历史。
 */
public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

    boolean existsBySessionIdAndQuestionIndex(String sessionId, int questionIndex);

    Optional<QuizAnswer> findFirstByUserIdAndWordKeyOrderByAnsweredAtDescIdDesc(Long userId, String wordKey);

    List<QuizAnswer> findBySessionIdOrderByQuestionIndexAsc(String sessionId);
}
