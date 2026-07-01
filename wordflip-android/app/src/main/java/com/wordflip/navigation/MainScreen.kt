package com.wordflip.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.wordflip.feature.books.BooksScreen
import com.wordflip.feature.groups.GroupsScreen
import com.wordflip.feature.settings.SettingsScreen
import com.wordflip.feature.stats.StatsScreen
import com.wordflip.feature.today.TodayScreen

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Today) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    val selected = tab == selectedTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            MainTab.Settings -> SettingsScreen(Modifier.padding(innerPadding))
            MainTab.Books -> BooksScreen(Modifier.padding(innerPadding))
            MainTab.Groups -> GroupsScreen(Modifier.padding(innerPadding))
            MainTab.Stats -> StatsScreen(Modifier.padding(innerPadding))
            MainTab.Today -> TodayScreen(Modifier.padding(innerPadding))
        }
    }
}
