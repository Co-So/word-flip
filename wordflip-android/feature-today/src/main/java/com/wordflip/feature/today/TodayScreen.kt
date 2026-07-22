package com.wordflip.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wordflip.core.model.navigation.StudyNavigation
import com.wordflip.core.model.today.TodayDashboard
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.apple.applePress
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.rememberWordFlipToast
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Apple 风格今日首页：保留原有刷新、Toast 和全部学习/测验导航行为。
 */
@Composable
fun TodayScreen(
    onNavigateToStudy: (StudyNavigation) -> Unit,
    onNavigateToQuiz: () -> Unit,
    /** 最近学习组进测验：source=recent + groupId。 */
    onNavigateToRecentQuiz: (groupId: Int, groupName: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadDashboard()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TodayUiEvent.Toast -> toast.show(event.message)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = AppleUi.colors.canvas,
        // Today 内层只保留显式状态栏安全区，底栏安全区继续由外层导航壳负责。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
        ) {
            when (val state = uiState) {
                TodayUiState.Loading -> TodayAppleSkeleton()
                is TodayUiState.Error -> TodayInlineError(
                    message = state.message,
                    onRetry = viewModel::loadDashboard,
                )
                is TodayUiState.Content -> TodayAppleContent(
                    dashboard = state.dashboard,
                    primary = resolveTodayPrimaryCard(state.dashboard),
                    viewModel = viewModel,
                    onNavigateToStudy = onNavigateToStudy,
                    onNavigateToQuiz = onNavigateToQuiz,
                    onNavigateToRecentQuiz = onNavigateToRecentQuiz,
                    onNotificationClick = { toast.show("提醒功能即将上线") },
                )
            }
        }
    }
}

/** 按产品层级固定编排今日内容，不在客户端重排任务优先级。 */
@Composable
private fun TodayAppleContent(
    dashboard: TodayDashboard,
    primary: TodayPrimaryCard,
    viewModel: TodayViewModel,
    onNavigateToStudy: (StudyNavigation) -> Unit,
    onNavigateToQuiz: () -> Unit,
    onNavigateToRecentQuiz: (groupId: Int, groupName: String) -> Unit,
    onNotificationClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        TodayGreetingHeader(
            date = dashboard.date,
            streakDays = dashboard.streakDays,
            onNotificationClick = onNotificationClick,
        )
        TodayHeroCard(
            primary = primary,
            onStudyClick = onNavigateToStudy,
            onRecentQuizClick = onNavigateToRecentQuiz,
            viewModel = viewModel,
        )
        TodayMetricsRow(stats = dashboard.stats)
        TodayTaskList(
            tasks = dashboard.tasks,
            subtitle = viewModel::buildTaskSubtitle,
            onNewWords = {
                viewModel.resolveTaskStudyNavigation(dashboard.tasks.newWords)
                    ?.let(onNavigateToStudy)
            },
            onDueReview = {
                viewModel.resolveTaskStudyNavigation(dashboard.tasks.dueReview)
                    ?.let(onNavigateToStudy)
            },
            onQuiz = onNavigateToQuiz,
        )
        TodayRecentGroups(
            groups = dashboard.recentGroups,
            onStudy = { group ->
                onNavigateToStudy(viewModel.resolveRecentStudyNavigation(group))
            },
            onQuiz = { group ->
                onNavigateToRecentQuiz(group.groupId, group.name)
            },
        )
        // 为浮动 Tab 栏保留视觉安全区，避免最后一张卡被遮挡。
        Spacer(modifier = Modifier.height(104.dp))
    }
}

/** 页首使用系统字号层级，集中展示问候、日期和连续学习。 */
@Composable
private fun TodayGreetingHeader(
    date: String,
    streakDays: Int,
    onNotificationClick: () -> Unit,
) {
    val colors = AppleUi.colors
    val interactionSource = remember { MutableInteractionSource() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = buildGreeting(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatLocalizedDate(date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.secondaryText,
                )
            }
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .applePress(interactionSource)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onNotificationClick,
                    ),
                shape = CircleShape,
                color = colors.elevatedSurface,
                tonalElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "通知",
                        modifier = Modifier.size(21.dp),
                        tint = colors.primaryText,
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.accent.copy(alpha = 0.12f),
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.accent,
                )
                Text(
                    text = "连续学习 $streakDays 天",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun buildGreeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 12 -> "早上好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }
}

private fun formatLocalizedDate(isoDate: String): String {
    return runCatching {
        val date = LocalDate.parse(isoDate)
        val formatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)
        date.format(formatter)
    }.getOrElse { isoDate }
}

/** 将 ISO lastStudiedAt 格式化为相对时间文案。 */
internal fun formatRelativeStudiedAt(isoDateTime: String): String {
    return runCatching {
        val instant = Instant.parse(isoDateTime)
        val minutes = Duration.between(instant, Instant.now()).toMinutes().coerceAtLeast(0)
        when {
            minutes < 1 -> "刚刚学习"
            minutes < 60 -> "$minutes 分钟前"
            minutes < 60 * 24 -> "${minutes / 60} 小时前"
            minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)} 天前"
            else -> {
                val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE))
            }
        }
    }.getOrElse { "最近学习" }
}
