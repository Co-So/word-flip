package com.wordflip.core.model.study

/**
 * POST /study/sessions 请求体，对齐 openapi StudySessionReportRequest。
 */
data class StudySessionReportRequest(
    val groupId: Int,
    val durationSec: Int? = null,
    val cardsViewed: Int? = null,
    val completedAt: String? = null,
)

/** POST /study/sessions 响应体 */
data class StudySessionReportResponse(
    val logDate: String,
    val streakDays: Int,
)
