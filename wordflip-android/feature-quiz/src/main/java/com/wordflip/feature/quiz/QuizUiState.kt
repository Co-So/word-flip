package com.wordflip.feature.quiz

import com.wordflip.core.model.quiz.QuizAnswerFeedback
import com.wordflip.core.model.quiz.QuizQuestionItem
import com.wordflip.core.model.quiz.QuizResultData

/** 测验页 UI 状态（REQ-QUIZ-1~10） */
sealed interface QuizUiState {
    data object Loading : QuizUiState

    data class Question(
        val sessionId: String,
        val currentIndex: Int,
        val totalQuestions: Int,
        val score: Int,
        val question: QuizQuestionItem,
        val userAnswer: String,
        val inputEnabled: Boolean,
        val feedback: QuizAnswerFeedback? = null,
    ) : QuizUiState {
        val progress: Float
            get() = if (totalQuestions > 0) currentIndex.toFloat() / totalQuestions else 0f
    }

    data class Result(val data: QuizResultData) : QuizUiState

    data class Error(val message: String) : QuizUiState
}
