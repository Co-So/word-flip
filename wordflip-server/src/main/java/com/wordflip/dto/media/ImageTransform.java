package com.wordflip.dto.media;

/**
 * 卡片图片变换参数，对齐 openapi ImageTransform（REQ-IMAGE-8～12）。
 */
public record ImageTransform(
        Double rotate,
        Double scale,
        Double offsetX,
        Double offsetY,
        Boolean showCn,
        ImageFilters filters
) {
    /**
     * 填充缺省值，保证入库 JSON 字段完整。
     */
    public ImageTransform withDefaults() {
        return new ImageTransform(
                rotate != null ? rotate : 0.0,
                scale != null ? scale : 1.0,
                offsetX != null ? offsetX : 0.0,
                offsetY != null ? offsetY : 0.0,
                showCn != null ? showCn : true,
                filters
        );
    }
}
