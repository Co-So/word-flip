package com.wordflip.repository;

import com.wordflip.domain.WordImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 卡片图片仓储；按 (userId, wordKey) 查询，供 ImageService 与后续 Study 聚合使用。
 */
public interface WordImageRepository extends JpaRepository<WordImage, Long> {

    Optional<WordImage> findByUserIdAndWordKey(Long userId, String wordKey);

    List<WordImage> findByUserIdAndWordKeyIn(Long userId, Collection<String> wordKeys);
}
