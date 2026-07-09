package com.wordflip.core.model.fake

import com.wordflip.core.model.quiz.QuizPrompt
import com.wordflip.core.model.quiz.QuizQuestionItem
import com.wordflip.core.model.quiz.QuizRating
import com.wordflip.core.model.quiz.QuizResultData
import com.wordflip.core.model.quiz.QuizSource
import com.wordflip.core.model.quiz.QuizWrongWord
import com.wordflip.core.model.study.MasteryLevel
import com.wordflip.core.model.study.MasterySnapshot
import java.util.UUID
import kotlin.math.roundToInt

/**
 * 默写测验 Mock 数据；题目来自 FakeStudyData，判题逻辑在 QuizViewModel 本地模拟。
 */
object FakeQuizData {

    private const val DEFAULT_LIMIT = 10

    /** 创建新 session 题目池（REQ-QUIZ-2 / REQ-NAV-6：每次进入重新抽题） */
    fun createSession(
        source: QuizSource,
        groupId: Int? = null,
        questionLimit: Int = DEFAULT_LIMIT,
    ): QuizSessionPayload {
        val words = when (source) {
            QuizSource.STUDY, QuizSource.GROUPS, QuizSource.RECENT -> {
                val payload = FakeStudyData.forGroup(groupId ?: 3)
                payload?.words.orEmpty()
            }
            QuizSource.TODAY, QuizSource.RETRY, QuizSource.ALL -> {
                // Mock：合并第 2、3 组词作为测验池（模拟到期复习 ∪ fuzzy/unknown）
                val group2 = FakeStudyData.forGroup(2)?.words.orEmpty()
                val group3 = FakeStudyData.forGroup(3)?.words.orEmpty()
                (group2 + group3).distinctBy { it.wordKey }
            }
        }.shuffled()

        val selected = words.take(questionLimit.coerceIn(1, 50))
        val questions = selected.mapIndexed { index, card ->
            QuizQuestionItem(
                questionIndex = index,
                wordKey = card.wordKey,
                expectedEn = card.en,
                prompt = QuizPrompt(
                    cn = card.cn,
                    pos = card.pos,
                    ph = card.ph,
                ),
                type = "dictation",
                options = null,
                detail = card.detail,
            )
        }

        return QuizSessionPayload(
            sessionId = UUID.randomUUID().toString(),
            questions = questions,
        )
    }

    /** Mock 掌握度变更：仅测验写入，此处模拟 applyQuizResult 三态（REQ-QUIZ-6） */
    fun mockMasteryAfterAnswer(
        wordKey: String,
        correct: Boolean,
        consecutiveWrongBefore: Int,
    ): Pair<MasterySnapshot, MasterySnapshot> {
        val before = MasterySnapshot(
            MasteryLevel.UNLEARNED,
            hasQuizHistory = consecutiveWrongBefore > 0,
            stability = if (consecutiveWrongBefore > 0) 20.0 else 0.0,
            heatLevel = if (consecutiveWrongBefore > 0) 1 else 0,
        )
        val after = when {
            correct -> MasterySnapshot(
                MasteryLevel.UNLEARNED, hasQuizHistory = true, stage = 2,
                stability = 25.0, heatLevel = 1,
            )
            consecutiveWrongBefore >= 1 -> MasterySnapshot(
                MasteryLevel.UNKNOWN, hasQuizHistory = true, stage = 0,
                stability = 5.0, heatLevel = 0,
            )
            else -> MasterySnapshot(
                MasteryLevel.FUZZY, hasQuizHistory = true, stage = 1,
                stability = 12.0, heatLevel = 1,
            )
        }
        return before to after
    }

    fun buildResult(
        sessionId: String,
        score: Int,
        total: Int,
        wrongWords: List<QuizWrongWord>,
    ): QuizResultData {
        val wrongCount = total - score
        val accuracy = if (total > 0) score.toFloat() / total else 0f
        val rating = when {
            accuracy >= 0.9f -> QuizRating.EXCELLENT
            accuracy >= 0.7f -> QuizRating.GOOD
            else -> QuizRating.KEEP_GOING
        }
        return QuizResultData(
            sessionId = sessionId,
            score = score,
            total = total,
            correctCount = score,
            wrongCount = wrongCount,
            accuracy = accuracy,
            rating = rating,
            wrongWords = wrongWords,
        )
    }

    fun accuracyPercent(accuracy: Float): Int = (accuracy * 100).roundToInt()
}

/** Mock session 载荷 */
data class QuizSessionPayload(
    val sessionId: String,
    val questions: List<QuizQuestionItem>,
)
