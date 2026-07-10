package com.wordflip.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wordflip.core.model.navigation.StudyNavigation
import com.wordflip.core.model.today.RecentGroup
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.StatTripleRow
import com.wordflip.core.ui.component.TaskRow
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.WordFlipTopBarAction
import com.wordflip.core.ui.component.rememberWordFlipToast
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 今日首页：问候、统计、最近学习组（主入口）、今日任务（REQ-TODAY）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onNavigateToStudy: (StudyNavigation) -> Unit,
    onNavigateToQuiz: () -> Unit,
    /** 最近学习组进测验：source=recent + groupId */
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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
        topBar = {
            when (val state = uiState) {
                is TodayUiState.Content -> TodayHeader(
                    date = state.dashboard.date,
                    streakDays = state.dashboard.streakDays,
                    onNotificationClick = {
                        // REQ-TODAY-8：提醒占位
                        toast.show("提醒功能即将上线")
                    },
                )
                else -> WordFlipTopBar(title = "今日")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = uiState) {
                TodayUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is TodayUiState.Error -> {
                    NetworkErrorView(
                        message = state.message,
                        onRetry = viewModel::loadDashboard,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                is TodayUiState.Content -> {
                    TodayContent(
                        state = state,
                        viewModel = viewModel,
                        onNavigateToStudy = onNavigateToStudy,
                        onNavigateToQuiz = onNavigateToQuiz,
                        onNavigateToRecentQuiz = onNavigateToRecentQuiz,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayHeader(
    date: String,
    streakDays: Int,
    onNotificationClick: () -> Unit,
) {
    WordFlipTopBar(
        title = buildGreeting(),
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StreakBadge(streakDays = streakDays)
                WordFlipTopBarAction(
                    icon = Icons.Outlined.Notifications,
                    contentDescription = "通知",
                    onClick = onNotificationClick,
                )
            }
        },
    )
}

@Composable
private fun TodayContent(
    state: TodayUiState.Content,
    viewModel: TodayViewModel,
    onNavigateToStudy: (StudyNavigation) -> Unit,
    onNavigateToQuiz: () -> Unit,
    onNavigateToRecentQuiz: (groupId: Int, groupName: String) -> Unit,
) {
    val dashboard = state.dashboard
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // REQ-TODAY-2：本地化日期含星期
        Text(
            text = formatLocalizedDate(dashboard.date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatTripleRow(
            masteredCount = dashboard.stats.masteredCount,
            dueReviewCount = dashboard.stats.dueReviewCount,
            completionPercent = dashboard.stats.completionPercent,
        )
        // 最近学习组上提为主入口（替代原底部「开始学习」）
        if (dashboard.recentGroups.isNotEmpty()) {
            Text(
                text = "最近学习",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dashboard.recentGroups.take(3).forEach { group ->
                    RecentGroupCard(
                        group = group,
                        onStudyClick = {
                            onNavigateToStudy(viewModel.resolveRecentStudyNavigation(group))
                        },
                        onQuizClick = { onNavigateToRecentQuiz(group.groupId, group.name) },
                    )
                }
            }
        }
        Text(
            text = "今日任务",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TaskRow(
                title = dashboard.tasks.newWords.label,
                subtitle = viewModel.buildTaskSubtitle(dashboard.tasks.newWords),
                count = dashboard.tasks.newWords.count,
                icon = Icons.Outlined.AutoStories,
                onClick = {
                    // REQ-TODAY-5
                    viewModel.resolveTaskStudyNavigation(dashboard.tasks.newWords)
                        ?.let(onNavigateToStudy)
                },
                contentDescription = "新词学习，${dashboard.tasks.newWords.count} 词",
            )
            TaskRow(
                title = dashboard.tasks.dueReview.label,
                subtitle = viewModel.buildTaskSubtitle(dashboard.tasks.dueReview),
                count = dashboard.tasks.dueReview.count,
                icon = Icons.Outlined.Schedule,
                onClick = {
                    viewModel.resolveTaskStudyNavigation(dashboard.tasks.dueReview)
                        ?.let(onNavigateToStudy)
                },
                contentDescription = "到期复习，${dashboard.tasks.dueReview.count} 词",
            )
            TaskRow(
                title = dashboard.tasks.quiz.label,
                subtitle = "到期复习 ∪ 模糊/不认识",
                count = dashboard.tasks.quiz.count,
                icon = Icons.Outlined.Spellcheck,
                onClick = onNavigateToQuiz,
                contentDescription = "默写测验，${dashboard.tasks.quiz.count} 题",
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** 最近组卡片：主区域进学习，右侧测验按钮进组测（REQ-TODAY-13） */
@Composable
private fun RecentGroupCard(
    group: RecentGroup,
    onStudyClick: () -> Unit,
    onQuizClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onStudyClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatRelativeStudiedAt(group.lastStudiedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onQuizClick) {
                Icon(
                    imageVector = Icons.Outlined.Quiz,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(text = "测验")
            }
        }
    }
}

@Composable
private fun StreakBadge(streakDays: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalFireDepartment,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = streakDays.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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

/** 将 ISO lastStudiedAt 格式化为相对时间文案 */
internal fun formatRelativeStudiedAt(isoDateTime: String): String {
    return runCatching {
        val instant = Instant.parse(isoDateTime)
        val minutes = Duration.between(instant, Instant.now()).toMinutes().coerceAtLeast(0)
        when {
            minutes < 1 -> "刚刚学习"
            minutes < 60 -> "${minutes} 分钟前"
            minutes < 60 * 24 -> "${minutes / 60} 小时前"
            minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)} 天前"
            else -> {
                val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE))
            }
        }
    }.getOrElse { "最近学习" }
}
