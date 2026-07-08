package com.wordflip.dto.today;

/**
 * 推荐学习分组，对齐 openapi recommendedStudy。
 */
public record RecommendedStudy(long groupId, String groupName, int wordCount, StudyReason reason) {
}
