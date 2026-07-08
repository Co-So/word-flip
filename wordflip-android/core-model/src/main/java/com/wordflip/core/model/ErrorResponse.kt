package com.wordflip.core.model

/**
 * 统一错误响应，对齐 openapi ErrorResponse；用于解析 4xx/5xx 响应体。
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: String? = null,
    val path: String? = null,
    val details: Map<String, Any>? = null,
)
