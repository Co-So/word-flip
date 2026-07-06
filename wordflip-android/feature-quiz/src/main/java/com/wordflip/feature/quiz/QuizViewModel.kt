package com.wordflip.feature.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.fake.FakeQuizData
import com.wordflip.core.model.quiz.QuizAnswerFeedback
import com.wordflip.core.model.quiz.QuizFeedbackType
import com.wordflip.core.model.quiz.QuizQuestionItem
import com.wordflip.core.model.quiz.QuizSource
import com.wordflip.core.model.quiz.QuizWrongWord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 默写测验 ViewModel；Mock 本地判题，掌握度变更仅模拟（真实环境走 applyQuizResult）。
 */
class QuizViewModel(
    private val source: QuizSource,
    private val groupId: Int?,
) : ViewModel() {

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private var questions: List<QuizQuestionItem> = emptyList()
    private var sessionId: String = ""
    private var currentIndex: Int = 0
    private var score: Int = 0
    private val wrongWords = mutableListOf<QuizWrongWord>()
    /** 本会话连续答错计数，用于 Mock 连续 2 错 → unknown（REQ-QUIZ-6） */
    private val consecutiveWrongByWord = mutableMapOf<String, Int>()
    /** 答对后自动下一题的协程，手动点「下一题」时取消 */
    private var autoAdvanceJob: Job? = null

    init {
        startSession()
    }

    /** REQ-NAV-6 / REQ-QUIZ-2：每次进入重新抽题并重置进度 */
    fun startSession() {
        viewModelScope.launch {
            _uiState.value = QuizUiState.Loading
            delay(200)
            val payload = FakeQuizData.createSession(
                source = source,
                groupId = groupId,
                questionLimit = 10,
            )
            if (payload.questions.isEmpty()) {
                _uiState.value = QuizUiState.Error("暂无测验题目")
                return@launch
            }
            questions = payload.questions
            sessionId = payload.sessionId
            currentIndex = 0
            score = 0
            wrongWords.clear()
            consecutiveWrongByWord.clear()
            emitQuestion(userAnswer = "", inputEnabled = true, feedback = null)
        }
    }

    fun onAnswerChange(value: String) {
        val state = _uiState.value as? QuizUiState.Question ?: return
        if (!state.inputEnabled) return
        _uiState.value = state.copy(
            userAnswer = value,
            practiceHint = if (state.consolidationActive) null else state.practiceHint,
            practicePassed = if (state.consolidationActive) false else state.practicePassed,
            feedback = if (state.consolidationActive) state.feedback else null,
        )
    }

    /** 提交：正式测验或巩固练习（由 consolidationActive 区分） */
    fun submitAnswer() {
        val state = _uiState.value as? QuizUiState.Question ?: return
        if (!state.inputEnabled || state.userAnswer.isBlank()) return
        if (state.consolidationActive) {
            submitPractice()
            return
        }
        submitOfficial(state)
    }

    /** 切换「盖住单词」：盖住后原位置改显中文 */
    fun toggleHintCovered() {
        val state = _uiState.value as? QuizUiState.Question ?: return
        if (!state.consolidationActive) return
        _uiState.value = state.copy(hintPanelCovered = !state.hintPanelCovered)
    }

    /** 进入下一题：巩固练习通过后，或答对反馈期间手动跳过等待 */
    fun goToNextQuestion() {
        val state = _uiState.value as? QuizUiState.Question ?: return
        when {
            state.consolidationActive -> {
                if (!state.practicePassed) return
                advanceAfterFeedback()
            }
            state.feedback?.type == QuizFeedbackType.CORRECT && !state.inputEnabled -> {
                autoAdvanceJob?.cancel()
                autoAdvanceJob = null
                advanceAfterFeedback()
            }
        }
    }

    private fun submitOfficial(state: QuizUiState.Question) {
        val question = state.question
        val trimmed = state.userAnswer.trim()
        val correct = trimmed.equals(question.expectedEn, ignoreCase = true)
        val wrongStreakBefore = if (correct) 0 else consecutiveWrongByWord[question.wordKey].orElse(0)

        val (before, after) = FakeQuizData.mockMasteryAfterAnswer(
            wordKey = question.wordKey,
            correct = correct,
            consecutiveWrongBefore = wrongStreakBefore,
        )

        val feedback = if (correct) {
            consecutiveWrongByWord.remove(question.wordKey)
            score += 1
            QuizAnswerFeedback(
                type = QuizFeedbackType.CORRECT,
                message = "✓ 正确！",
                masteryBefore = before,
                masteryAfter = after,
            )
        } else {
            val newStreak = wrongStreakBefore + 1
            consecutiveWrongByWord[question.wordKey] = newStreak
            wrongWords += QuizWrongWord(
                wordKey = question.wordKey,
                en = question.expectedEn,
                cn = question.prompt.cn,
                userAnswer = trimmed,
            )
            QuizAnswerFeedback(
                type = QuizFeedbackType.WRONG,
                message = if (newStreak >= 2) "✗ 错误 · 已标记不认识" else "✗ 错误 · 已标记模糊",
                expectedEn = question.expectedEn,
                masteryBefore = before,
                masteryAfter = after,
            )
        }

        if (correct) {
            _uiState.value = state.copy(
                inputEnabled = false,
                feedback = feedback,
                consolidationActive = false,
            )
            autoAdvanceJob?.cancel()
            autoAdvanceJob = viewModelScope.launch {
                delay(FEEDBACK_DELAY_MS)
                advanceAfterFeedback()
                autoAdvanceJob = null
            }
        } else {
            // 答错：下方展示提示区，原输入框清空继续练习；须手动点「下一题」
            _uiState.value = state.copy(
                inputEnabled = true,
                userAnswer = "",
                feedback = feedback,
                consolidationActive = true,
                wrongAttemptAnswer = trimmed,
                practiceHint = null,
                practicePassed = false,
                hintPanelCovered = false,
            )
        }
    }

    /** 巩固练习判题：不修改得分/掌握度/错题记录 */
    private fun submitPractice() {
        val state = _uiState.value as? QuizUiState.Question ?: return
        val trimmed = state.userAnswer.trim()
        if (trimmed.isBlank()) return

        val correct = trimmed.equals(state.question.expectedEn, ignoreCase = true)
        _uiState.value = state.copy(
            practiceHint = if (correct) "对了！可以进入下一题" else "再试一次",
            practicePassed = correct,
        )
    }

    private fun advanceAfterFeedback() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
        currentIndex += 1
        if (currentIndex >= questions.size) {
            _uiState.value = QuizUiState.Result(
                FakeQuizData.buildResult(
                    sessionId = sessionId,
                    score = score,
                    total = questions.size,
                    wrongWords = wrongWords.toList(),
                ),
            )
        } else {
            emitQuestion(userAnswer = "", inputEnabled = true, feedback = null)
        }
    }

    private fun emitQuestion(
        userAnswer: String,
        inputEnabled: Boolean,
        feedback: QuizAnswerFeedback?,
    ) {
        val question = questions.getOrNull(currentIndex) ?: return
        _uiState.value = QuizUiState.Question(
            sessionId = sessionId,
            currentIndex = currentIndex,
            totalQuestions = questions.size,
            score = score,
            question = question,
            userAnswer = userAnswer,
            inputEnabled = inputEnabled,
            feedback = feedback,
            consolidationActive = false,
            wrongAttemptAnswer = null,
            practiceHint = null,
            practicePassed = false,
            hintPanelCovered = false,
        )
    }

    private fun Int?.orElse(default: Int): Int = this ?: default

    class Factory(
        private val source: QuizSource,
        private val groupId: Int?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QuizViewModel(source, groupId) as T
        }
    }

    companion object {
        /** REQ-QUIZ-7：答对短暂展示反馈后自动下一题，可手动跳过 */
        const val FEEDBACK_DELAY_MS = 600L
    }
}
