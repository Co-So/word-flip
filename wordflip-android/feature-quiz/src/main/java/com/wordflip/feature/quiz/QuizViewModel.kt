package com.wordflip.feature.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.quiz.QuizAnswerFeedback
import com.wordflip.core.model.quiz.QuizFeedbackType
import com.wordflip.core.model.quiz.QuizQuestionItem
import com.wordflip.core.model.quiz.QuizSource
import com.wordflip.core.model.quiz.QuizWrongCard
import com.wordflip.core.model.quiz.toQuestionItem
import com.wordflip.core.model.quiz.toResultData
import com.wordflip.core.model.settings.apiValue
import com.wordflip.core.model.settings.parseQuestionTypesCsv
import com.wordflip.core.network.quiz.QuizRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 测验 ViewModel；走 POST /quiz/sessions 与 answer（掌握度唯一写入口）。
 * 支持默写与选择题（selectedKey）；题型/开测模式来自导航 query。
 */
@HiltViewModel
class QuizViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val quizRepository: QuizRepository,
) : ViewModel() {

    private val source: QuizSource = parseQuizSource(savedStateHandle.get<String>("source").orEmpty())
    private val groupId: Int? = savedStateHandle.get<Int>("groupId")?.takeIf { it >= 0 }
    /** study/组测入口：按题数出题（1–50） */
    private val wordLimit: Int = savedStateHandle.get<Int>("wordLimit")?.coerceIn(1, 50) ?: DEFAULT_QUESTION_LIMIT
    private val questionTypes: List<String>? = parseQuestionTypesCsv(
        savedStateHandle.get<String>("questionTypes"),
    ).map { it.apiValue() }.ifEmpty { null }
    private val launchMode: String? = savedStateHandle.get<String>("launchMode")
        ?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private var sessionId: String = ""
    private var currentQuestion: QuizQuestionItem? = null
    private var currentIndex: Int = 0
    private var totalQuestions: Int = 0
    private var score: Int = 0
    /** 答错巩固完成后待展示的下一题（服务端已在首次判题时写入掌握度） */
    private var pendingNextQuestion: QuizQuestionItem? = null
    private var pendingCompleted: Boolean = false
    private val wrongCards = mutableListOf<QuizWrongCard>()
    /** 同一题网络重试复用同一幂等键，成功后才移除。 */
    private val answerRequestIds = mutableMapOf<Int, String>()
    private var autoAdvanceJob: Job? = null

    init {
        startSession()
    }

    /** REQ-NAV-6 / REQ-QUIZ-2：每次进入重新抽题并重置进度 */
    fun startSession() {
        viewModelScope.launch {
            _uiState.value = QuizUiState.Loading
            val limit = resolveQuestionLimit()
            quizRepository.createSession(
                source = source,
                groupId = groupId,
                questionLimit = limit,
                groupIds = groupId?.let { listOf(it) },
                questionTypes = questionTypes,
                launchMode = launchMode,
            ).fold(
                onSuccess = { created ->
                    sessionId = created.sessionId
                    totalQuestions = created.totalQuestions
                    currentIndex = created.currentIndex
                    score = created.score
                    wrongCards.clear()
                    answerRequestIds.clear()
                    pendingNextQuestion = null
                    pendingCompleted = false
                    currentQuestion = created.question.toQuestionItem()
                    emitQuestion(userAnswer = "", inputEnabled = true, feedback = null)
                },
                onFailure = { error ->
                    _uiState.value = QuizUiState.Error(error.message ?: "创建测验失败")
                },
            )
        }
    }

    /** 题数来自路由 wordLimit（组测/设置默认题数）；缺省 10 */
    private fun resolveQuestionLimit(): Int = wordLimit.coerceIn(1, 50)

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

    fun submitAnswer() {
        val state = _uiState.value as? QuizUiState.Question ?: return
        if (!state.inputEnabled || state.userAnswer.isBlank()) return
        if (state.consolidationActive) {
            submitPractice()
            return
        }
        submitOfficial(state, answer = state.userAnswer.trim(), selectedKey = null)
    }

    /** 选择题：选中选项 key 后提交 selectedKey */
    fun submitChoice(selectedKey: String) {
        val state = _uiState.value as? QuizUiState.Question ?: return
        if (!state.inputEnabled || state.consolidationActive) return
        if (!state.question.isChoice) return
        submitOfficial(state, answer = null, selectedKey = selectedKey)
    }

    fun toggleHintCovered() {
        val state = _uiState.value as? QuizUiState.Question ?: return
        if (!state.consolidationActive) return
        _uiState.value = state.copy(hintPanelCovered = !state.hintPanelCovered)
    }

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

    private fun submitOfficial(
        state: QuizUiState.Question,
        answer: String?,
        selectedKey: String?,
    ) {
        val question = state.question
        val displayAnswer = selectedKey ?: answer.orEmpty()
        val requestId = answerRequestIds.getOrPut(question.questionIndex) { UUID.randomUUID().toString() }
        viewModelScope.launch {
            _uiState.value = state.copy(inputEnabled = false, userAnswer = displayAnswer)
            quizRepository.submitAnswer(
                sessionId = sessionId,
                requestId = requestId,
                questionIndex = question.questionIndex,
                answer = answer,
                selectedKey = selectedKey,
            ).fold(
                onSuccess = { result ->
                    answerRequestIds.remove(question.questionIndex)
                    score = result.session.score
                    currentIndex = result.session.currentIndex
                    totalQuestions = result.session.totalQuestions
                    pendingNextQuestion = result.session.nextQuestion?.toQuestionItem()
                    pendingCompleted = result.session.status == "completed"

                    val feedback = result.toFeedback()
                    val revealedEn = result.expectedEn.orEmpty()
                    val revealedAnswer = result.expectedAnswer?.takeIf { it.isNotBlank() }
                        ?: revealedEn
                    val questionWithAnswer = question.copy(
                        expectedEn = revealedEn.ifBlank { question.expectedEn },
                        prompt = question.prompt.copy(
                            cn = when {
                                question.type.equals("choice_en_cn", ignoreCase = true) &&
                                    revealedAnswer.isNotBlank() -> revealedAnswer
                                else -> question.prompt.cn
                            },
                        ),
                    )
                    // 选择题答错：展示选项 label，而非 wordKey
                    val wrongDisplay = if (selectedKey != null) {
                        question.options?.firstOrNull { it.key == selectedKey }?.label
                            ?: selectedKey
                    } else {
                        displayAnswer
                    }

                    if (result.correct) {
                        _uiState.value = state.copy(
                            question = questionWithAnswer,
                            userAnswer = displayAnswer,
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
                        wrongCards += QuizWrongCard(
                            cardId = question.cardId,
                            lexemeId = question.lexemeId,
                            en = revealedEn.ifBlank { question.expectedEn },
                            cn = questionWithAnswer.prompt.cn,
                            userAnswer = wrongDisplay,
                        )
                        // 选择题答错：仍进入巩固（默写正确答案）
                        _uiState.value = state.copy(
                            question = questionWithAnswer,
                            inputEnabled = true,
                            userAnswer = "",
                            feedback = feedback,
                            consolidationActive = true,
                            wrongAttemptAnswer = wrongDisplay,
                            practiceHint = null,
                            practicePassed = false,
                            hintPanelCovered = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.value = state.copy(
                        inputEnabled = true,
                        feedback = null,
                    )
                    _uiState.value = QuizUiState.Error(error.message ?: "提交答案失败")
                },
            )
        }
    }

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

        if (pendingCompleted) {
            viewModelScope.launch {
                quizRepository.loadResult(sessionId).fold(
                    onSuccess = { payload ->
                        _uiState.value = QuizUiState.Result(payload.toResultData())
                    },
                    onFailure = { error ->
                        _uiState.value = QuizUiState.Error(error.message ?: "加载测验结果失败")
                    },
                )
            }
            return
        }

        val next = pendingNextQuestion
        if (next == null) {
            _uiState.value = QuizUiState.Error("下一题加载失败")
            return
        }
        currentQuestion = next
        pendingNextQuestion = null
        emitQuestion(userAnswer = "", inputEnabled = true, feedback = null)
    }

    private fun emitQuestion(
        userAnswer: String,
        inputEnabled: Boolean,
        feedback: QuizAnswerFeedback?,
    ) {
        val question = currentQuestion ?: return
        _uiState.value = QuizUiState.Question(
            sessionId = sessionId,
            currentIndex = question.questionIndex,
            totalQuestions = totalQuestions,
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

    companion object {
        const val FEEDBACK_DELAY_MS = 600L
        private const val DEFAULT_QUESTION_LIMIT = 10
    }
}

private fun com.wordflip.core.model.quiz.AnswerResult.toFeedback(): QuizAnswerFeedback {
    val type = if (correct) QuizFeedbackType.CORRECT else QuizFeedbackType.WRONG
    val message = if (correct) {
        "✓ 正确！"
    } else {
        "✗ 错误 · 已按 Again 调度"
    }
    return QuizAnswerFeedback(
        type = type,
        message = message,
        expectedEn = expectedEn,
        expectedAnswer = expectedAnswer ?: expectedEn,
        masteryBefore = masteryUpdate?.before,
        masteryAfter = masteryUpdate?.after,
    )
}
