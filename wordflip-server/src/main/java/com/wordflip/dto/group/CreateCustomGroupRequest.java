package com.wordflip.dto.group;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建自定义分组请求（POST /groups/custom）。
 */
public class CreateCustomGroupRequest {

    @NotEmpty
    @Size(min = 1, max = 500)
    private List<String> wordKeys;

    @Size(max = 64)
    private String name;

    public List<String> getWordKeys() {
        return wordKeys;
    }

    public void setWordKeys(List<String> wordKeys) {
        this.wordKeys = wordKeys;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
