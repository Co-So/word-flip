package com.wordflip.dto.study;

import com.wordflip.domain.GroupSource;

import java.util.List;

/**
 * 学习页载荷，对齐 openapi StudyGroupPayload。
 */
public record StudyGroupPayload(
        StudyGroupInfo group,
        List<WordCardDto> words
) {

    public record StudyGroupInfo(long id, String name, GroupSource source) {
    }
}
