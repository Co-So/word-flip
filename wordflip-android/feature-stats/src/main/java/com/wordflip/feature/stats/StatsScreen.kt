package com.wordflip.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.component.AchievementRow
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.StatsQuadGrid
import com.wordflip.core.ui.component.StudyActivityChart
import com.wordflip.core.ui.component.WordFlipTopBar

/**
 * 统计 & 成就页（REQ-STATS-1~3）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { WordFlipTopBar(title = "统计 & 成就") },
        containerColor = AppleUi.colors.canvas,
    ) { innerPadding ->
        when (val state = uiState) {
            StatsUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is StatsUiState.Error -> {
                NetworkErrorView(
                    message = state.message,
                    onRetry = viewModel::loadStats,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is StatsUiState.Content -> {
                val summary = state.dashboard.summary
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    item {
                        StreakHero(streakDays = summary.streakDays)
                    }
                    item {
                        StatsSection(title = "学习概览") {
                            // 保持原有四项指标及 ViewModel 的正确率取整口径。
                            StatsQuadGrid(
                                masteredCount = summary.masteredCount,
                                streakDays = summary.streakDays,
                                quizAccuracyPercent = viewModel.quizAccuracyPercent(summary.quizAccuracy),
                                totalStudyDays = summary.totalStudyDays,
                            )
                        }
                    }
                    item {
                        StatsSection(title = "近三月活动") {
                            StudyActivityChart(days = state.dashboard.heatmapDays)
                        }
                    }
                    item {
                        StatsSection(title = "成就时间线") {
                            AppleGroupedSurface(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                state.dashboard.achievements.forEachIndexed { index, achievement ->
                                    AchievementRow(item = achievement)
                                    if (index < state.dashboard.achievements.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 52.dp),
                                            color = AppleUi.colors.separator,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 将连续学习放在统计页首要层级，不改变原始 streak 数值。 */
@Composable
private fun StreakHero(
    streakDays: Int,
    modifier: Modifier = Modifier,
) {
    AppleGroupedSurface(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = AppleUi.colors.accent.copy(alpha = 0.14f),
                tonalElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "连续",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppleUi.colors.accent,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "连续学习",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppleUi.colors.secondaryText,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = streakDays.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = AppleUi.colors.primaryText,
                    )
                    Text(
                        text = "天",
                        modifier = Modifier.padding(bottom = 5.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleUi.colors.secondaryText,
                    )
                }
                Text(
                    text = "每次学习都在延续你的节奏",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppleUi.colors.secondaryText,
                )
            }
        }
    }
}

@Composable
private fun StatsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = AppleUi.colors.secondaryText,
    )
}
