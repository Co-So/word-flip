package com.wordflip.core.model.media

/**
 * 卡片图片 API 响应，对齐 openapi WordImageResponse。
 */
data class WordImageResponse(
    val wordKey: String,
    val hasImage: Boolean = false,
    val imageUrl: String? = null,
    val storageKey: String? = null,
    val transform: ImageTransform? = null,
    val updatedAt: String? = null,
)

/**
 * 污渍 API 响应，对齐 openapi WordStainResponse。
 */
data class WordStainResponse(
    val wordKey: String,
    val hidden: Boolean = false,
    val config: StainConfig? = null,
)

/**
 * PUT /words/{wordKey}/stain 请求体。
 */
data class StainUpdateRequest(
    val action: String,
    val typeFilter: List<String>? = null,
    val config: StainConfig? = null,
)

data class StainBatchRequest(
    val typeFilter: List<String>? = null,
)

data class StainBatchResponse(
    val groupId: Long = 0,
    val updatedCount: Int = 0,
)
