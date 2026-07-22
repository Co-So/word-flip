package com.wordflip.dto.stain;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * PUT /learning/cards/{cardId}/stain 请求体：action = regenerate|set_hidden|set_visible|replace。
 */
public class StainUpdateRequest {

    @NotBlank
    private String action;

    /** 可选类型筛选：coffee|ink|highlight|crayon|random-line */
    private List<String> typeFilter;

    /** replace 时必填 */
    private StainConfig config;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public List<String> getTypeFilter() {
        return typeFilter;
    }

    public void setTypeFilter(List<String> typeFilter) {
        this.typeFilter = typeFilter;
    }

    public StainConfig getConfig() {
        return config;
    }

    public void setConfig(StainConfig config) {
        this.config = config;
    }
}
