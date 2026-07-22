package com.wordflip.core.model.group

/**
 * 创建自定义分组请求，对齐 openapi `CreateCustomGroupRequest`。
 * cardIds 须来自当前计划未入组卡片池。
 */
data class CreateCustomGroupRequest(
    val cardIds: List<Long>,
    val name: String? = null,
)
