package com.wordflip.core.model.quiz

import com.wordflip.core.model.study.MasterySnapshot

/** POST /quiz/sessions 请求体 */
data class CreateQuizSessionRequest(
    val source: String = "today",
    val groupId: Int? = null,
    val questionLimit: Int = 10,
)

/** POST /quiz/sessions 201 响应 */
data class QuizSessionCreated(
    val sessionId: String,
    val status: String,
    val totalQuestions: Int,
    val currentIndex: Int,
    val score: Int,
    val question: QuizQuestionPayload,
)

/** openapi QuizQuestion（不含标准答案） */
data class QuizQuestionPayload(
    val questionIndex: Int,
    val wordKey: String,
    val prompt: QuizPrompt,
)

/** POST .../answer 请求 */
data class SubmitAnswerRequest(
    val questionIndex: Int,
    val answer: String,
)

/** POST .../answer 200 响应 */
data class AnswerResult(
    val correct: Boolean,
    val expectedEn: String? = null,
    val feedback: String,
    val masteryUpdate: MasteryUpdatePayload? = null,
    val session: AnswerSessionProgress,
)

data class MasteryUpdatePayload(
    val wordKey: String,
    val before: MasterySnapshot,
    val after: MasterySnapshot,
)

data class AnswerSessionProgress(
    val status: String,
    val score: Int,
    val currentIndex: Int,
    val totalQuestions: Int,
    val nextQuestion: QuizQuestionPayload? = null,
)

/** GET .../result 响应 */
data class QuizResultPayload(
    val sessionId: String,
    val score: Int,
    val total: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val accuracy: Float,
    val rating: String,
    val wrongWords: List<QuizWrongWord>,
)

/** QuizQuestionPayload → 本地测验题目（expectedEn 答错后由服务端返回） */
fun QuizQuestionPayload.toQuestionItem(expectedEn: String = ""): QuizQuestionItem = QuizQuestionItem(
    questionIndex = questionIndex,
    wordKey = wordKey,
    expectedEn = expectedEn,
    prompt = prompt,
    detail = null,
)

fun QuizResultPayload.toResultData(): QuizResultData = QuizResultData(
    sessionId = sessionId,
    score = score,
    total = total,
    correctCount = correctCount,
    wrongCount = wrongCount,
    accuracy = accuracy,
    rating = when (rating.lowercase()) {
        "excellent" -> QuizRating.EXCELLENT
        "good" -> QuizRating.GOOD
        else -> QuizRating.KEEP_GOING
    },
    wrongWords = wrongWords,
)
