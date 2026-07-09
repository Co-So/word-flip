package com.wordflip.repository;

import com.wordflip.domain.QuizAnswer;
import com.wordflip.domain.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 测验作答仓储；连续答错按同 skill 查最近一条。
 */
public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

    boolean existsBySessionIdAndQuestionIndex(String sessionId, int questionIndex);

    Optional<QuizAnswer> findFirstByUserIdAndWordKeyAndSkillOrderByAnsweredAtDescIdDesc(
            Long userId,
            String wordKey,
            Skill skill
    );

    List<QuizAnswer> findBySessionIdOrderByQuestionIndexAsc(String sessionId);
}
