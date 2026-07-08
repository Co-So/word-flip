package com.wordflip.repository;

import com.wordflip.domain.UserBookSelection;
import com.wordflip.domain.UserBookSelectionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 用户词书勾选仓储。
 */
public interface UserBookSelectionRepository extends JpaRepository<UserBookSelection, UserBookSelectionId> {

    @Query("SELECT ubs.id.bookId FROM UserBookSelection ubs WHERE ubs.id.userId = :userId")
    List<Long> findBookIdsByUserId(@Param("userId") Long userId);

    /** 按勾选写入顺序返回词书 ID（selected_at 递增，REQ-BOOK-23 book_order） */
    @Query("SELECT ubs.id.bookId FROM UserBookSelection ubs WHERE ubs.id.userId = :userId ORDER BY ubs.selectedAt ASC")
    List<Long> findBookIdsByUserIdOrderBySelectedAtAsc(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserBookSelection ubs WHERE ubs.id.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    boolean existsByIdUserIdAndIdBookId(Long userId, Long bookId);
}
