package com.wordflip.dto.quiz;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * POST /quiz/sessions 请求体。
 */
public class CreateQuizSessionRequest {

    private String source = "today";
    private Long groupId;
    private List<Long> groupIds;

    @Min(1)
    @Max(50)
    private Integer questionLimit = 10;

    /** dictation / choice_en_cn / choice_cn_en；mixed 可省略 */
    private List<String> questionTypes;

    /** mixed | free_select */
    private String launchMode;

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

    public List<Long> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(List<Long> groupIds) {
        this.groupIds = groupIds;
    }

    public Integer getQuestionLimit() {
        return questionLimit;
    }

    public void setQuestionLimit(Integer questionLimit) {
        this.questionLimit = questionLimit;
    }

    public List<String> getQuestionTypes() {
        return questionTypes;
    }

    public void setQuestionTypes(List<String> questionTypes) {
        this.questionTypes = questionTypes;
    }

    public String getLaunchMode() {
        return launchMode;
    }

    public void setLaunchMode(String launchMode) {
        this.launchMode = launchMode;
    }
}
