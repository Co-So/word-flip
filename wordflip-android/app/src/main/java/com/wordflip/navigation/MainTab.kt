package com.wordflip.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    Settings("设置", Icons.Outlined.Settings),
    Books("词书", Icons.Outlined.MenuBook),
    Groups("分组", Icons.Outlined.GridView),
    Stats("统计", Icons.Outlined.BarChart),
    Today("今日", Icons.Outlined.Today),
}
