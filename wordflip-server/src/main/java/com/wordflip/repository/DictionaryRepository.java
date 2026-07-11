package com.wordflip.repository;

import com.wordflip.domain.Dictionary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DictionaryRepository extends JpaRepository<Dictionary, String> {

    List<Dictionary> findAllByOrderBySortOrderAscIdAsc();
}
