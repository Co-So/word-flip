package com.wordflip.dto.stain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 单条污渍实例，对齐 openapi StainConfig.stains[]（坐标 0~1 归一化）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StainItemDto {

    /** coffee|ink|highlight|crayon|random-line */
    private String type;
    private Double x;
    private Double y;
    private Double size;
    private Double rotation;
    private Double intensity;
    private Long seed;
    private Integer layerOrder;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double getX() {
        return x;
    }

    public void setX(Double x) {
        this.x = x;
    }

    public Double getY() {
        return y;
    }

    public void setY(Double y) {
        this.y = y;
    }

    public Double getSize() {
        return size;
    }

    public void setSize(Double size) {
        this.size = size;
    }

    public Double getRotation() {
        return rotation;
    }

    public void setRotation(Double rotation) {
        this.rotation = rotation;
    }

    public Double getIntensity() {
        return intensity;
    }

    public void setIntensity(Double intensity) {
        this.intensity = intensity;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public Integer getLayerOrder() {
        return layerOrder;
    }

    public void setLayerOrder(Integer layerOrder) {
        this.layerOrder = layerOrder;
    }
}
