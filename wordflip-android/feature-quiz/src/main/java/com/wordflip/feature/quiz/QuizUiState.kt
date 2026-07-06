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
        /**
         * 答错巩固阶段：首次判题已计入得分/错题；原输入框继续练习，结果不计分。
         */
        val consolidationActive: Boolean = false,
        /** 首次测验答错的提交内容 */
        val wrongAttemptAnswer: String? = null,
        /** 巩固练习判题提示（不影响测验得分） */
        val practiceHint: String? = null,
        /** 巩固练习是否已通过；为 true 时才可点「下一题」 */
        val practicePassed: Boolean = false,
        /** 是否盖住英文单词；盖住后在原位置显示中文释义 */
        val hintPanelCovered: Boolean = false,
    ) : QuizUiState {
        val progress: Float
            get() = if (totalQuestions > 0) currentIndex.toFloat() / totalQuestions else 0f
    }

    data class Result(val data: QuizResultData) : QuizUiState

    data class Error(val message: String) : QuizUiState
}
