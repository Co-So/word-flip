package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wordflip.domain.GroupStrategy;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 自动分组候选卡排序规则测试。
 */
class AutoGroupCardOrdererTest {

    private final AutoGroupCardOrderer orderer = new AutoGroupCardOrderer();

    @Test
    void frequencySortsRanksFirstAndFallsBackToBookOrderForMissingRanks() {
        List<AutoGroupCardOrderer.Candidate> candidates = List.of(
                new AutoGroupCardOrderer.Candidate(101L, 3, null),
                new AutoGroupCardOrderer.Candidate(102L, 1, 20),
                new AutoGroupCardOrderer.Candidate(103L, 2, null),
                new AutoGroupCardOrderer.Candidate(104L, 0, 5)
        );

        assertThat(cardIds(orderer.order(candidates, GroupStrategy.frequency, 7L, 9L)))
                .containsExactly(104L, 102L, 103L, 101L);
    }

    @Test
    void randomIsStableForSameUserAndPlanRegardlessOfCandidateInputOrder() {
        List<AutoGroupCardOrderer.Candidate> candidates = List.of(
                new AutoGroupCardOrderer.Candidate(101L, 0, null),
                new AutoGroupCardOrderer.Candidate(102L, 1, null),
                new AutoGroupCardOrderer.Candidate(103L, 2, null),
                new AutoGroupCardOrderer.Candidate(104L, 3, null),
                new AutoGroupCardOrderer.Candidate(105L, 4, null),
                new AutoGroupCardOrderer.Candidate(106L, 5, null)
        );
        List<AutoGroupCardOrderer.Candidate> reversed = candidates.reversed();

        List<Long> first = cardIds(orderer.order(candidates, GroupStrategy.random, 7L, 9L));
        List<Long> second = cardIds(orderer.order(reversed, GroupStrategy.random, 7L, 9L));

        assertThat(first).containsExactlyElementsOf(second);
        assertThat(first).isNotEqualTo(cardIds(candidates));
    }

    @Test
    void bookOrderSortsByBookPosition() {
        List<AutoGroupCardOrderer.Candidate> candidates = List.of(
                new AutoGroupCardOrderer.Candidate(101L, 2, 1),
                new AutoGroupCardOrderer.Candidate(102L, 0, 3),
                new AutoGroupCardOrderer.Candidate(103L, 1, 2)
        );

        assertThat(cardIds(orderer.order(candidates, GroupStrategy.book_order, 7L, 9L)))
                .containsExactly(102L, 103L, 101L);
    }

    private List<Long> cardIds(List<AutoGroupCardOrderer.Candidate> candidates) {
        return candidates.stream().map(AutoGroupCardOrderer.Candidate::cardId).toList();
    }
}
