package com.wordflip.repository;

import com.wordflip.domain.WordStain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 污渍配置仓储；按 (userId, wordKey) 读写 word_stains。
 */
public interface WordStainRepository extends JpaRepository<WordStain, Long> {

    Optional<WordStain> findByUserIdAndWordKey(Long userId, String wordKey);

    List<WordStain> findByUserIdAndWordKeyIn(Long userId, Collection<String> wordKeys);
}
