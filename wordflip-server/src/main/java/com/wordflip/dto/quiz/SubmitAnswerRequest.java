package com.wordflip.dto.quiz;

import jakarta.validation.constraints.NotNull;

/**
 * POST /quiz/sessions/{sessionId}/answer 请求体。
 * 默写用 answer；选择用 selectedKey。
 */
public class SubmitAnswerRequest {

    @NotNull
    private Integer questionIndex;

    private String answer;

    private String selectedKey;

    public Integer getQuestionIndex() {
        return questionIndex;
    }

    public void setQuestionIndex(Integer questionIndex) {
        this.questionIndex = questionIndex;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getSelectedKey() {
        return selectedKey;
    }

    public void setSelectedKey(String selectedKey) {
        this.selectedKey = selectedKey;
    }
}
