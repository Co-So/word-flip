package com.wordflip.core.model.settings

/**
 * 组详情热力展示模式，对齐 openapi `HeatDisplayMode`。
 * combined 取两 skill 较低档；dictation/choice 单轨；free 客户端切换（服务端默认按 combined）。
 */
enum class HeatDisplayMode {
    COMBINED,
    DICTATION,
    CHOICE,
    FREE,
}

fun HeatDisplayMode.label(): String = when (this) {
    HeatDisplayMode.COMBINED -> "综合（取低）"
    HeatDisplayMode.DICTATION -> "仅默写"
    HeatDisplayMode.CHOICE -> "仅选择"
    HeatDisplayMode.FREE -> "自由切换"
}

fun HeatDisplayMode.storageValue(): String = name.lowercase()

fun parseHeatDisplayMode(value: String?): HeatDisplayMode = when (value?.lowercase()) {
    "dictation" -> HeatDisplayMode.DICTATION
    "choice" -> HeatDisplayMode.CHOICE
    "free" -> HeatDisplayMode.FREE
    else -> HeatDisplayMode.COMBINED
}

/**
 * 开测模式，对齐 openapi `QuizLaunchMode`。
 * mixed 混合直开；free_select 开测前选题型与题数。
 */
enum class QuizLaunchMode {
    MIXED,
    FREE_SELECT,
}

fun QuizLaunchMode.label(): String = when (this) {
    QuizLaunchMode.MIXED -> "混合直开"
    QuizLaunchMode.FREE_SELECT -> "开测前选择"
}

fun QuizLaunchMode.storageValue(): String = name.lowercase()

fun parseQuizLaunchMode(value: String?): QuizLaunchMode = when (value?.lowercase()) {
    "free_select" -> QuizLaunchMode.FREE_SELECT
    else -> QuizLaunchMode.MIXED
}

/** 题型，对齐 openapi `QuestionType` */
enum class QuestionType {
    DICTATION,
    CHOICE_EN_CN,
    CHOICE_CN_EN,
}

fun QuestionType.apiValue(): String = name.lowercase()

fun QuestionType.label(): String = when (this) {
    QuestionType.DICTATION -> "默写"
    QuestionType.CHOICE_EN_CN -> "英选中"
    QuestionType.CHOICE_CN_EN -> "中选英"
}

fun parseQuestionType(value: String?): QuestionType = when (value?.lowercase()) {
    "choice_en_cn" -> QuestionType.CHOICE_EN_CN
    "choice_cn_en" -> QuestionType.CHOICE_CN_EN
    else -> QuestionType.DICTATION
}

fun parseQuestionTypesCsv(csv: String?): List<QuestionType> {
    if (csv.isNullOrBlank()) return emptyList()
    return csv.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { parseQuestionType(it) }
        .distinct()
}
