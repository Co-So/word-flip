package com.wordflip.core.model.navigation

/**
 * 进入学习页的导航参数，供今日/分组详情等 feature 共用。
 */
data class StudyNavigation(
    val groupId: Int,
    val groupName: String,
    val wordCount: Int,
)
