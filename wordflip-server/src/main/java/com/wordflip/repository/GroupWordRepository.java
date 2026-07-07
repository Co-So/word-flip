package com.wordflip.repository;

import com.wordflip.domain.GroupWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

/**
 * 分组单词关联仓储。
 */
public interface GroupWordRepository extends JpaRepository<GroupWord, Long> {

    @Query("SELECT gw.wordKey FROM GroupWord gw WHERE gw.userId = :userId")
    Set<String> findWordKeysByUserId(@Param("userId") Long userId);

    List<GroupWord> findByGroupIdOrderBySortOrderAsc(Long groupId);
}
