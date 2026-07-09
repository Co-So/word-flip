package com.wordflip.dto.stain;

import java.util.List;

/**
 * POST /groups/{groupId}/stains/batch 请求体；typeFilter 可选。
 */
public class StainBatchRequest {

    private List<String> typeFilter;

    public List<String> getTypeFilter() {
        return typeFilter;
    }

    public void setTypeFilter(List<String> typeFilter) {
        this.typeFilter = typeFilter;
    }
}
