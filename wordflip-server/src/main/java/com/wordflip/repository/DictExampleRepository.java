package com.wordflip.repository;

import com.wordflip.domain.DictExample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DictExampleRepository extends JpaRepository<DictExample, Long> {

    List<DictExample> findBySenseIdInOrderBySenseIdAscSortOrderAsc(Collection<Long> senseIds);
}
