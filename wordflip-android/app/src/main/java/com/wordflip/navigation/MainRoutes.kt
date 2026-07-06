package com.wordflip.navigation

import android.net.Uri

/** 主界面 Tab 与子页路由（A-16 最小子集） */
object MainRoutes {
    const val SETTINGS = "settings"
    const val BOOKS = "books"
    const val GROUPS = "groups"
    const val STATS = "stats"
    const val TODAY = "today"

    const val STUDY = "study/{groupId}?groupName={groupName}&wordCount={wordCount}"
    const val GROUP_DETAIL = "group_detail/{groupId}?groupName={groupName}&stainMode={stainMode}"
    const val SNAPSHOT = "snapshot/{groupId}?groupName={groupName}"
    const val QUIZ = "quiz/{source}?groupId={groupId}&nonce={nonce}"

    fun studyRoute(groupId: Int, groupName: String, wordCount: Int): String {
        val encodedName = Uri.encode(groupName)
        return "study/$groupId?groupName=$encodedName&wordCount=$wordCount"
    }

    fun groupDetailRoute(groupId: Int, groupName: String, stainMode: Boolean = false): String {
        val encodedName = Uri.encode(groupName)
        return "group_detail/$groupId?groupName=$encodedName&stainMode=$stainMode"
    }

    fun snapshotRoute(groupId: Int, groupName: String): String {
        val encodedName = Uri.encode(groupName)
        return "snapshot/$groupId?groupName=$encodedName"
    }

    fun quizRoute(
        source: String = "today",
        groupId: Int = -1,
        nonce: Long = System.currentTimeMillis(),
    ): String = "quiz/$source?groupId=$groupId&nonce=$nonce"

    val tabRoutes = setOf(SETTINGS, BOOKS, GROUPS, STATS, TODAY)

    fun tabForRoute(route: String?): MainTab? = when (route?.substringBefore("?")) {
        SETTINGS -> MainTab.Settings
        BOOKS -> MainTab.Books
        GROUPS -> MainTab.Groups
        STATS -> MainTab.Stats
        TODAY -> MainTab.Today
        else -> null
    }
}
