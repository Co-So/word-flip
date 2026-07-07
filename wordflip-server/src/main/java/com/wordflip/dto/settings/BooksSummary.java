package com.wordflip.dto.settings;

/**
 * 词书汇总：distinct 词数、预估组数、未入组词数。
 */
public class BooksSummary {

    private long distinctSelectedCount;
    private int estimatedGroupCount;
    private long unassignedCount;

    public BooksSummary(long distinctSelectedCount, int estimatedGroupCount, long unassignedCount) {
        this.distinctSelectedCount = distinctSelectedCount;
        this.estimatedGroupCount = estimatedGroupCount;
        this.unassignedCount = unassignedCount;
    }

    public long getDistinctSelectedCount() {
        return distinctSelectedCount;
    }

    public int getEstimatedGroupCount() {
        return estimatedGroupCount;
    }

    public long getUnassignedCount() {
        return unassignedCount;
    }
}
