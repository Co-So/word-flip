package com.wordflip.repository;

import com.wordflip.domain.Skill;
import com.wordflip.domain.WordSkillProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 按题型的单词进度仓储。
 */
public interface WordSkillProgressRepository extends JpaRepository<WordSkillProgress, Long> {

    Optional<WordSkillProgress> findByUserIdAndWordKeyAndSkill(Long userId, String wordKey, Skill skill);

    List<WordSkillProgress> findByUserIdAndWordKeyIn(Long userId, Collection<String> wordKeys);

    List<WordSkillProgress> findByUserIdAndWordKeyInAndSkill(Long userId, Collection<String> wordKeys, Skill skill);
}
