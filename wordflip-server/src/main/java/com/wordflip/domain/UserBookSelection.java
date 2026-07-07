package com.wordflip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 用户勾选的词书（PUT /settings 全量替换；存在即选中）。
 */
@Entity
@Table(name = "user_book_selection")
@Getter
@Setter
@NoArgsConstructor
public class UserBookSelection {

    @EmbeddedId
    private UserBookSelectionId id;

    @Column(name = "selected_at", nullable = false)
    private Instant selectedAt = Instant.now();

    public UserBookSelection(Long userId, Long bookId) {
        this.id = new UserBookSelectionId(userId, bookId);
        this.selectedAt = Instant.now();
    }

    public Long getUserId() {
        return id.getUserId();
    }

    public Long getBookId() {
        return id.getBookId();
    }
}
