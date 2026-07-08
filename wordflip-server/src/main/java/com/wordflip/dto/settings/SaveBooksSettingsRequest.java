package com.wordflip.dto.settings;

import com.wordflip.domain.GroupStrategy;
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

    /** 自动分组策略；省略时保持原值或默认 book_order */
    private GroupStrategy groupStrategy;

    /** true 时删除全部 auto 组并按当前词书重建（REQ-BOOK-26）；与增量 append 互斥 */
    private Boolean regroup;

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

    public GroupStrategy getGroupStrategy() {
        return groupStrategy;
    }

    public void setGroupStrategy(GroupStrategy groupStrategy) {
        this.groupStrategy = groupStrategy;
    }

    public Boolean getRegroup() {
        return regroup;
    }

    public void setRegroup(Boolean regroup) {
        this.regroup = regroup;
    }

    /** 校验 groupSize 是否为允许值 10/20/30/50 */
    public boolean isGroupSizeValid() {
        return groupSize != null && List.of(10, 20, 30, 50).contains(groupSize);
    }
}
