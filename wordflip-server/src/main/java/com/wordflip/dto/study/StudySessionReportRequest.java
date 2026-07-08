package com.wordflip.dto.study;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * POST /study/sessions 请求体。
 */
public class StudySessionReportRequest {

    @NotNull
    private Long groupId;

    private Integer durationSec;

    private Integer wordsViewed;

    private Instant completedAt;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Integer getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(Integer durationSec) {
        this.durationSec = durationSec;
    }

    public Integer getWordsViewed() {
        return wordsViewed;
    }

    public void setWordsViewed(Integer wordsViewed) {
        this.wordsViewed = wordsViewed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
