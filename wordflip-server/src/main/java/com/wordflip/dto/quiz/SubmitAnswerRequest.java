package com.wordflip.dto.quiz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /quiz/sessions/{sessionId}/answer 请求体。
 */
public class SubmitAnswerRequest {

    @NotNull
    private Integer questionIndex;

    @NotBlank
    private String answer;

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
}
