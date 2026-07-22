package com.wordflip.dto.study;

import com.wordflip.dto.learning.LearningCardDetailResponse;
import java.util.List;

/**
 * 学习页分组信息与词书专属学习卡。
 */
public record StudyGroupPayload(StudyGroupInfo group, List<LearningCardDetailResponse> cards) {

    public record StudyGroupInfo(long id, String name, String source) {
    }
}
