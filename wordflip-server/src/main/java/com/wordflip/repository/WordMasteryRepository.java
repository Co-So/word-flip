package com.wordflip.repository;

import com.wordflip.domain.WordMastery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 掌握度仓储；Groups 读 API 聚合 stats/progress 时使用。
 */
public interface WordMasteryRepository extends JpaRepository<WordMastery, Long> {

    List<WordMastery> findByUserIdAndWordKeyIn(Long userId, Collection<String> wordKeys);

    Optional<WordMastery> findByUserIdAndWordKey(Long userId, String wordKey);
}
