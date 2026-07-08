package com.wordflip.dto.quiz;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * POST /quiz/sessions 请求体，对齐 openapi CreateQuizSessionRequest。
 */
public class CreateQuizSessionRequest {

    private String source = "today";
    private Long groupId;

    @Min(1)
    @Max(50)
    private Integer questionLimit = 10;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Integer getQuestionLimit() {
        return questionLimit;
    }

    public void setQuestionLimit(Integer questionLimit) {
        this.questionLimit = questionLimit;
    }
}
