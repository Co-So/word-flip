package com.wordflip.core.model.media

/**
 * 图片变换参数，对齐 openapi `ImageTransform`（REQ-SNAP-5）。
 */
data class ImageTransform(
    val rotate: Float = 0f,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val showCn: Boolean = true,
)

/**
 * 图片滤镜参数，对齐 openapi `ImageTransform.filters`。
 */
data class ImageFilters(
    val brightness: Float = 100f,
    val contrast: Float = 100f,
    val saturate: Float = 100f,
    val grayscale: Float = 0f,
    val sepia: Float = 0f,
)

/** 污渍类型，对齐 openapi `StainConfig.stains[].type` */
enum class StainType {
    COFFEE,
    INK,
    HIGHLIGHT,
    CRAYON,
    RANDOM_LINE,
}

/** 污渍生成模式 */
enum class StainMode {
    RANDOM,
    SINGLE,
    MULTI,
}

/**
 * 单条污渍实例，坐标为 0~1 归一化（REQ-STAIN-2）。
 */
data class StainItem(
    val type: StainType,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float,
    val intensity: Float,
    val seed: Long,
    val layerOrder: Int,
)

/**
 * 污渍配置，对齐 openapi `StainConfig`。
 */
data class StainConfig(
    val seed: Long,
    val mode: StainMode = StainMode.RANDOM,
    val density: Int = 50,
    val aging: Int = 20,
    val stains: List<StainItem> = emptyList(),
)

/** Mock 本地存储的图片记录（P3-A06 占位，待接 MinIO presigned URL） */
data class StoredWordImage(
    val localUri: String,
    val transform: ImageTransform = ImageTransform(),
    val filters: ImageFilters = ImageFilters(),
    val showCnOnImage: Boolean = true,
)
