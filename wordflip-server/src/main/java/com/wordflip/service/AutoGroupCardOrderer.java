package com.wordflip.service;

import com.wordflip.domain.GroupStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 只负责排列本次尚未入组的学习卡，不修改已有分组成员。
 */
public final class AutoGroupCardOrderer {

    /**
     * 按用户设置的自动分组策略返回候选卡副本。
     */
    public List<Candidate> order(
            List<Candidate> candidates, GroupStrategy strategy, long userId, long planId
    ) {
        List<Candidate> ordered = new ArrayList<>(candidates);
        Comparator<Candidate> bookOrder = Comparator.comparingInt(Candidate::bookOrder)
                .thenComparingLong(Candidate::cardId);
        switch (strategy) {
            case book_order -> ordered.sort(bookOrder);
            case frequency -> ordered.sort(
                    Comparator.comparing(Candidate::frequencyRank,
                                    Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(bookOrder)
            );
            case random -> {
                // 先规范输入顺序，确保稳定随机不受数据库返回顺序影响。
                ordered.sort(bookOrder);
                long seed = 31L * userId + planId;
                java.util.Collections.shuffle(ordered, new Random(seed));
            }
        }
        return List.copyOf(ordered);
    }

    /**
     * 自动分组所需的最小候选卡投影。
     */
    public record Candidate(long cardId, int bookOrder, Integer frequencyRank) {
    }
}
