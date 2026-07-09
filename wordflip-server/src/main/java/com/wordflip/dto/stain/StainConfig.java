package com.wordflip.dto.stain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 污渍配置，对齐 openapi StainConfig。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StainConfig {

    private Long seed;
    /** random|single|multi */
    private String mode;
    private Integer density;
    private Integer aging;
    private List<StainItemDto> stains = new ArrayList<>();

    public StainConfig() {
    }

    public StainConfig(Long seed) {
        this.seed = seed;
        this.mode = "random";
        this.density = 50;
        this.aging = 20;
        this.stains = new ArrayList<>();
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Integer getDensity() {
        return density;
    }

    public void setDensity(Integer density) {
        this.density = density;
    }

    public Integer getAging() {
        return aging;
    }

    public void setAging(Integer aging) {
        this.aging = aging;
    }

    public List<StainItemDto> getStains() {
        return stains;
    }

    public void setStains(List<StainItemDto> stains) {
        this.stains = stains != null ? stains : new ArrayList<>();
    }
}
