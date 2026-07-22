package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wordflip.repository.FsrsReviewStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 双层记忆与审计事务编排测试。
 */
@ExtendWith(MockitoExtension.class)
class FsrsReviewServiceTest {

    @Mock
    private FsrsReviewStore store;
    @Mock
    private FsrsSchedulerService scheduler;

    @Test
    void effectiveAnswerWritesEventBeforeUpdatingBothMemories() {
        var now = Instant.parse("2026-07-16T00:00:00Z");
        var requestId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var command = new FsrsReviewCommand(requestId, 1L, 2L, 3L, 4L, "dictation", "dictation", true, now);
        var before = FsrsMemorySnapshot.newCard(now);
        var after = new FsrsMemorySnapshot("learning", 0, 1, 5, now.plusSeconds(600), now, 1, 0, 0, 0);
        var scheduled = new FsrsScheduleResult("good", before, after);
        when(store.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(store.lockCardMemory(1L, 3L, "dictation", now)).thenReturn(before);
        when(scheduler.schedule(3L, before, true, now)).thenReturn(scheduled);

        var result = new FsrsReviewService(store, scheduler).applyQuizResult(command);

        assertThat(result.rating()).isEqualTo("good");
        var order = inOrder(store);
        order.verify(store).lockCardMemory(1L, 3L, "dictation", now);
        order.verify(store).lockLexemeMemory(1L, 4L, "dictation");
        order.verify(store).insertReviewEvent(command, scheduled);
        order.verify(store).updateCardMemory(1L, 3L, "dictation", after);
        order.verify(store).updateLexemeMemory(1L, 4L, "dictation", true, now);
    }

    @Test
    void duplicateRequestReturnsStoredResultWithoutRescheduling() {
        var requestId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var now = Instant.parse("2026-07-16T00:00:00Z");
        var stored = new FsrsScheduleResult("again", FsrsMemorySnapshot.newCard(now), FsrsMemorySnapshot.newCard(now));
        when(store.findByRequestId(requestId)).thenReturn(Optional.of(stored));

        var result = new FsrsReviewService(store, scheduler).applyQuizResult(
                new FsrsReviewCommand(requestId, 1L, 2L, 3L, 4L, "choice", "choice_en_cn", false, now)
        );

        assertThat(result).isSameAs(stored);
        verify(scheduler, never()).schedule(3L, stored.before(), false, now);
    }
}
