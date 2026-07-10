package com.wordflip.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wordflip.feature.books.BookDetailScreen
import com.wordflip.feature.books.BooksScreen
import com.wordflip.feature.groups.CustomGroupScreen
import com.wordflip.feature.groups.GroupDetailScreen
import com.wordflip.feature.groups.GroupsScreen
import com.wordflip.feature.settings.SettingsScreen
import com.wordflip.feature.stats.StatsScreen
import com.wordflip.feature.quiz.QuizScreen
import com.wordflip.feature.quiz.parseQuizSource
import com.wordflip.feature.snapshot.SnapshotScreen
import com.wordflip.feature.study.StudyScreen
import com.wordflip.feature.today.TodayScreen

@Composable
fun MainScreen(
    settingsPreferences: com.wordflip.feature.settings.SettingsPreferences,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore("?")
    val showBottomBar = currentRoute in MainRoutes.tabRoutes
    var joinLearningBookId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        // 子页各自 TopAppBar 已处理状态栏；此处仅预留底部导航栏 inset，避免顶部双重留白
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    MainTab.entries.forEach { tab ->
                        val route = tab.route
                        val selected = currentRoute == route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
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
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainRoutes.TODAY,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(MainRoutes.SETTINGS) {
                SettingsScreen(
                    preferences = settingsPreferences,
                    onLogout = onLogout,
                )
            }
            composable(MainRoutes.BOOKS) {
                BooksScreen(
                    onNavigateToCustomGroup = {
                        navController.navigate(MainRoutes.CUSTOM_GROUP)
                    },
                    onNavigateToBookDetail = { bookId ->
                        navController.navigate(MainRoutes.bookDetailRoute(bookId))
                    },
                    joinLearningBookId = joinLearningBookId,
                    onJoinLearningConsumed = { joinLearningBookId = null },
                )
            }
            composable(MainRoutes.GROUPS) {
                GroupsScreen(
                    onNavigateToGroupDetail = { groupId, groupName ->
                        navController.navigate(
                            MainRoutes.groupDetailRoute(groupId, groupName),
                        )
                    },
                    onNavigateToSnapshot = { groupId, groupName ->
                        navController.navigate(
                            MainRoutes.snapshotRoute(groupId, groupName),
                        )
                    },
                    onNavigateToStainMode = { groupId, groupName ->
                        navController.navigate(
                            MainRoutes.groupDetailRoute(groupId, groupName, stainMode = true),
                        )
                    },
                )
            }
            composable(MainRoutes.STATS) {
                StatsScreen()
            }
            composable(MainRoutes.TODAY) {
                TodayScreen(
                    onNavigateToStudy = { nav ->
                        navController.navigate(
                            MainRoutes.studyRoute(nav.groupId, nav.groupName, nav.wordCount),
                        )
                    },
                    onNavigateToQuiz = {
                        navController.navigate(MainRoutes.quizRoute(source = "today"))
                    },
                    onNavigateToRecentQuiz = { groupId, _ ->
                        navController.navigate(
                            MainRoutes.quizRoute(source = "recent", groupId = groupId),
                        )
                    },
                )
            }
            composable(
                route = MainRoutes.GROUP_DETAIL,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.IntType },
                    navArgument("groupName") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("stainMode") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val groupId = entry.arguments?.getInt("groupId") ?: 0
                val groupName = entry.arguments?.getString("groupName").orEmpty()
                val stainMode = entry.arguments?.getBoolean("stainMode") ?: false
                GroupDetailScreen(
                    groupId = groupId,
                    groupName = groupName,
                    initialStainMode = stainMode,
                    settingsPreferences = settingsPreferences,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStudy = { nav ->
                        navController.navigate(
                            MainRoutes.studyRoute(nav.groupId, nav.groupName, nav.wordCount),
                        )
                    },
                    onNavigateToQuiz = { source, gId, wordLimit, questionTypes, launchMode ->
                        navController.navigate(
                            MainRoutes.quizRoute(
                                source = source,
                                groupId = gId,
                                wordLimit = wordLimit,
                                questionTypes = questionTypes,
                                launchMode = launchMode,
                            ),
                        )
                    },
                )
            }
            composable(
                route = MainRoutes.SNAPSHOT,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.IntType },
                    navArgument("groupName") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val groupId = entry.arguments?.getInt("groupId") ?: 0
                val groupName = entry.arguments?.getString("groupName").orEmpty()
                SnapshotScreen(
                    groupId = groupId,
                    groupName = groupName,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(
                route = MainRoutes.STUDY,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.IntType },
                    navArgument("groupName") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("wordCount") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                ),
            ) { entry ->
                val groupId = entry.arguments?.getInt("groupId") ?: 0
                val groupName = entry.arguments?.getString("groupName").orEmpty()
                val wordCount = entry.arguments?.getInt("wordCount") ?: 0
                StudyScreen(
                    groupId = groupId,
                    groupName = groupName,
                    wordCount = wordCount,
                    settingsPreferences = settingsPreferences,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToQuiz = {
                        navController.navigate(
                            MainRoutes.quizRoute(
                                source = "study",
                                groupId = groupId,
                                wordLimit = wordCount,
                            ),
                        )
                    },
                )
            }
            composable(
                route = MainRoutes.QUIZ,
                arguments = listOf(
                    navArgument("source") {
                        type = NavType.StringType
                        defaultValue = "today"
                    },
                    navArgument("groupId") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument("wordLimit") {
                        type = NavType.IntType
                        defaultValue = 10
                    },
                    navArgument("questionTypes") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("launchMode") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("nonce") {
                        type = NavType.StringType
                        defaultValue = "0"
                    },
                ),
            ) { entry ->
                val source = parseQuizSource(entry.arguments?.getString("source").orEmpty())
                val groupIdArg = entry.arguments?.getInt("groupId") ?: -1
                val groupId = groupIdArg.takeIf { it >= 0 }
                val nonce = entry.arguments?.getString("nonce").orEmpty()
                QuizScreen(
                    source = source,
                    groupId = groupId,
                    viewModelKey = "quiz-$nonce",
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(MainRoutes.CUSTOM_GROUP) {
                CustomGroupScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSaved = {
                        navController.popBackStack()
                    },
                )
            }
            composable(
                route = MainRoutes.BOOK_DETAIL,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType },
                ),
            ) {
                BookDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onJoinLearning = { bookId ->
                        joinLearningBookId = bookId
                        navController.popBackStack(MainRoutes.BOOKS, inclusive = false)
                    },
                )
            }
        }
    }
}

private val MainTab.route: String
    get() = when (this) {
        MainTab.Settings -> MainRoutes.SETTINGS
        MainTab.Books -> MainRoutes.BOOKS
        MainTab.Groups -> MainRoutes.GROUPS
        MainTab.Stats -> MainRoutes.STATS
        MainTab.Today -> MainRoutes.TODAY
    }
