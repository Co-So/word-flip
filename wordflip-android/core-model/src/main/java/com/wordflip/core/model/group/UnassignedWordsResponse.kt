package com.wordflip.core.model.group

import com.wordflip.core.model.study.WordSummary

/** 未入组词池响应，对齐 openapi `UnassignedWordsResponse`（含 PageMeta） */
data class UnassignedWordsResponse(
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val words: List<WordSummary> = emptyList(),
)
