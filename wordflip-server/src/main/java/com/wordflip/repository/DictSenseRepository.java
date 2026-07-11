package com.wordflip.repository;

import com.wordflip.domain.DictSense;
import com.wordflip.domain.DictSenseQuality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DictSenseRepository extends JpaRepository<DictSense, Long> {

    @Query("""
            SELECT s FROM DictSense s
            WHERE s.dictId = :dictId AND s.wordKey IN :keys
            ORDER BY s.wordKey ASC, s.sortOrder ASC, s.id ASC
            """)
    List<DictSense> findByDictIdAndWordKeyInOrdered(
            @Param("dictId") String dictId,
            @Param("keys") Collection<String> keys
    );

    @Query("""
            SELECT s FROM DictSense s
            WHERE s.dictId = :dictId
              AND s.wordKey IN :keys
              AND s.primary = true
              AND s.quality = :quality
            """)
    List<DictSense> findPrimariesByDictIdAndWordKeyInAndQuality(
            @Param("dictId") String dictId,
            @Param("keys") Collection<String> keys,
            @Param("quality") DictSenseQuality quality
    );

    @Query("""
            SELECT s FROM DictSense s
            WHERE s.id IN :ids
            """)
    List<DictSense> findByIdIn(@Param("ids") Collection<Long> ids);

    boolean existsByDictIdAndWordKey(String dictId, String wordKey);
}
