package com.wordflip.core.model.group

import com.wordflip.core.model.study.WordSummary

/** 未入组词池响应，对齐 openapi `UnassignedWordsResponse`（Mock 仅用 words） */
data class UnassignedWordsResponse(
    val words: List<WordSummary>,
    val total: Int = words.size,
)
