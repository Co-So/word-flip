package com.wordflip.repository;

import com.wordflip.domain.DictWord;
import com.wordflip.domain.DictWordId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DictWordRepository extends JpaRepository<DictWord, DictWordId> {

    List<DictWord> findByDictIdAndWordKeyIn(String dictId, Collection<String> wordKeys);

    boolean existsByDictIdAndWordKey(String dictId, String wordKey);

    /** @deprecated 多词典后请用 {@link #existsByDictIdAndWordKey} */
    @Deprecated
    default boolean existsByWordKey(String wordKey) {
        return existsByDictIdAndWordKey(com.wordflip.domain.DictionaryIds.CURATED, wordKey);
    }
}
