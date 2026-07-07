package com.wordflip.dto.settings;

import com.wordflip.domain.ThemeMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 保存词书勾选与分组大小请求（PUT /settings）。
 */
public class SaveBooksSettingsRequest {

    @NotNull
    private List<Long> bookIds;

    @NotNull
    private Integer groupSize;

    public List<Long> getBookIds() {
        return bookIds;
    }

    public void setBookIds(List<Long> bookIds) {
        this.bookIds = bookIds;
    }

    public Integer getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(Integer groupSize) {
        this.groupSize = groupSize;
    }

    /** 校验 groupSize 是否为允许值 10/20/30/50 */
    public boolean isGroupSizeValid() {
        return groupSize != null && List.of(10, 20, 30, 50).contains(groupSize);
    }
}
