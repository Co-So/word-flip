package com.wordflip.core.model.quiz

import com.wordflip.core.model.settings.QuestionType
import com.wordflip.core.model.settings.apiValue
import com.wordflip.core.model.study.MasterySnapshot
import com.wordflip.core.model.study.WordDetail

/**
 * 测验入口来源，对齐 openapi `CreateQuizSessionRequest.source`。
 * today/study/retry 沿用原入口；groups/all/recent 为组测扩展。
 */
enum class QuizSource {
    TODAY,
    STUDY,
    RETRY,
    GROUPS,
    ALL,
    RECENT,
}

/** 题目提示，对齐 openapi `QuizQuestion.prompt` */
data class QuizPrompt(
    val cn: String,
    val pos: String? = null,
    val ph: String? = null,
    /** choice_en_cn（英→中）题干英文；服务端仅该题型填充 */
    val en: String? = null,
)

/** 选择题选项，对齐 openapi `QuizQuestion.options[]` */
data class QuizOption(
    val key: String,
    val label: String,
)

/**
 * 单题快照，对齐 openapi `QuizQuestion` + 标准答案（判题用）。
 */
data class QuizQuestionItem(
    val questionIndex: Int,
    val wordKey: String,
    val expectedEn: String,
    val prompt: QuizPrompt,
    /** 题型；缺省默写以兼容旧响应 */
    val type: String = QuestionType.DICTATION.apiValue(),
    /** 选择题选项；dictation 为 null */
    val options: List<QuizOption>? = null,
    /** 错题巩固：词义详情（Mock 来自 WordCard.detail） */
    val detail: WordDetail? = null,
) {
    val isChoice: Boolean
        get() = type.startsWith("choice_")
}

/** 判题反馈类型，对齐 openapi `AnswerResult.feedback` */
enum class QuizFeedbackType {
    CORRECT,
    WRONG,
}

/** Mock 判题结果；真实环境由 POST /quiz/sessions/{id}/answer 返回 */
data class QuizAnswerFeedback(
    val type: QuizFeedbackType,
    val message: String,
    val expectedEn: String? = null,
    /** 按题型展示的正确答案文案 */
    val expectedAnswer: String? = null,
    val masteryBefore: MasterySnapshot? = null,
    val masteryAfter: MasterySnapshot? = null,
)

/** 错题条目，对齐 openapi `QuizResult.wrongWords` */
data class QuizWrongWord(
    val wordKey: String,
    val en: String,
    val cn: String,
    val userAnswer: String,
)

/** 测验结果，对齐 openapi `QuizResult` */
data class QuizResultData(
    val sessionId: String,
    val score: Int,
    val total: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val accuracy: Float,
    val rating: QuizRating,
    val wrongWords: List<QuizWrongWord>,
)

/** 结果评价档，对齐 openapi `QuizResult.rating`（REQ-QUIZ-9） */
enum class QuizRating {
    EXCELLENT,
    GOOD,
    KEEP_GOING,
}

fun QuizRating.label(): String = when (this) {
    QuizRating.EXCELLENT -> "太棒了！"
    QuizRating.GOOD -> "不错！"
    QuizRating.KEEP_GOING -> "继续加油"
}

fun QuizRating.emoji(): String = when (this) {
    QuizRating.EXCELLENT -> "🎉"
    QuizRating.GOOD -> "👍"
    QuizRating.KEEP_GOING -> "💪"
}
