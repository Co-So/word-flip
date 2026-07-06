package com.wordflip.core.image

import com.wordflip.core.model.media.ImageFilters

/**
 * 图片滤镜预设（P3-A05 简化版）：替代多滑条，降低编辑页操作复杂度。
 */
enum class ImageFilterPreset(val label: String, val filters: ImageFilters) {
    NORMAL("原图", ImageFilters()),
    BRIGHT("明亮", ImageFilters(brightness = 115f, contrast = 108f)),
    VIVID("鲜明", ImageFilters(saturate = 140f, contrast = 110f)),
    VINTAGE("复古", ImageFilters(sepia = 40f, contrast = 95f, saturate = 88f)),
    MONO("黑白", ImageFilters(grayscale = 100f)),
}

/** 从当前滤镜反推最接近的预设（保存后再次打开编辑器时高亮） */
fun ImageFilters.matchPreset(): ImageFilterPreset {
    return ImageFilterPreset.entries.minByOrNull { preset ->
        val a = preset.filters
        listOf(
            kotlin.math.abs(brightness - a.brightness),
            kotlin.math.abs(contrast - a.contrast),
            kotlin.math.abs(saturate - a.saturate),
            kotlin.math.abs(grayscale - a.grayscale),
            kotlin.math.abs(sepia - a.sepia),
        ).sum()
    } ?: ImageFilterPreset.NORMAL
}
