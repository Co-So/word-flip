package com.wordflip.core.model.quiz

import com.wordflip.core.model.settings.QuestionType
import com.wordflip.core.model.settings.apiValue
import com.wordflip.core.model.study.MasterySnapshot

/** POST /quiz/sessions 请求体 */
data class CreateQuizSessionRequest(
    val source: String = "today",
    val groupId: Int? = null,
    val groupIds: List<Int>? = null,
    val questionLimit: Int = 10,
    val questionTypes: List<String>? = null,
    val launchMode: String? = null,
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
    val type: String = QuestionType.DICTATION.apiValue(),
    val prompt: QuizPrompt,
    val options: List<QuizOption>? = null,
)

/** POST .../answer 请求 */
data class SubmitAnswerRequest(
    val questionIndex: Int,
    val answer: String? = null,
    /** 选择题选中选项 key；默写题可省略 */
    val selectedKey: String? = null,
)

/** POST .../answer 200 响应 */
data class AnswerResult(
    val correct: Boolean,
    val expectedEn: String? = null,
    /** 答错时按题型展示的正确答案（英选中=中文，其余=英文） */
    val expectedAnswer: String? = null,
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
fun QuizQuestionPayload.toQuestionItem(expectedEn: String = ""): QuizQuestionItem {
    // 英选中：预填英文题干，避免 prompt.en 缺失时只能显示空串
    val seededEn = expectedEn.ifBlank {
        if (type.equals("choice_en_cn", ignoreCase = true)) {
            prompt.en?.takeIf { it.isNotBlank() } ?: wordKey
        } else {
            ""
        }
    }
    return QuizQuestionItem(
        questionIndex = questionIndex,
        wordKey = wordKey,
        expectedEn = seededEn,
        prompt = prompt,
        type = type,
        options = options,
        detail = null,
    )
}

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
