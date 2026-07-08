package com.wordflip.repository;

import com.wordflip.domain.WordFreqRank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 全局词频序查询，供 frequency 分组策略批量取 rank。
 */
public interface WordFreqRankRepository extends JpaRepository<WordFreqRank, String> {

    @Query("SELECT w FROM WordFreqRank w WHERE w.wordKey IN :wordKeys")
    List<WordFreqRank> findByWordKeyIn(@Param("wordKeys") Collection<String> wordKeys);
}
