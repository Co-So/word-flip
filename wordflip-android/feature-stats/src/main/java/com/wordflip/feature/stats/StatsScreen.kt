package com.wordflip.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        SectionTitle("学习数据")
                        StatsQuadGrid(
                            masteredCount = summary.masteredCount,
                            streakDays = summary.streakDays,
                            quizAccuracyPercent = viewModel.quizAccuracyPercent(summary.quizAccuracy),
                            totalStudyDays = summary.totalStudyDays,
                        )
                    }
                    item {
                        SectionTitle("学习日历")
                        StudyActivityChart(days = state.dashboard.heatmapDays)
                    }
                    item {
                        SectionTitle("成就")
                    }
                    items(state.dashboard.achievements, key = { it.id }) { achievement ->
                        AchievementRow(item = achievement)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
