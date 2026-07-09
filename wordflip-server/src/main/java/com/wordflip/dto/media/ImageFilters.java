package com.wordflip.dto.media;

/**
 * 图片滤镜参数，对齐 openapi ImageTransform.filters（REQ-IMAGE-11）。
 */
public record ImageFilters(
        Double brightness,
        Double contrast,
        Double saturate,
        Double grayscale,
        Double sepia
) {
}
