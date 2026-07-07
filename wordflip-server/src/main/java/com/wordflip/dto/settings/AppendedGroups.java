package com.wordflip.dto.settings;

import java.util.List;

/**
 * 增量追加的分组摘要。
 */
public class AppendedGroups {

    private int count;
    private List<AppendedGroupItem> groups;

    public AppendedGroups(int count, List<AppendedGroupItem> groups) {
        this.count = count;
        this.groups = groups;
    }

    public static AppendedGroups empty() {
        return new AppendedGroups(0, List.of());
    }

    public int getCount() {
        return count;
    }

    public List<AppendedGroupItem> getGroups() {
        return groups;
    }

    public record AppendedGroupItem(long id, String name, int wordCount) {
    }
}
