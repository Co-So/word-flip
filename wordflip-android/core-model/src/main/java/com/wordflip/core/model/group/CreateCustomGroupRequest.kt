package com.wordflip.core.model.group

/**
 * 创建自定义分组请求，对齐 openapi `CreateCustomGroupRequest`。
 * wordKeys 须来自未入组词池（GET /words/unassigned）。
 */
data class CreateCustomGroupRequest(
    val wordKeys: List<String>,
    val name: String? = null,
)
