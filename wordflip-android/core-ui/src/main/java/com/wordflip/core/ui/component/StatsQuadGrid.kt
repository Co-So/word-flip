package com.wordflip.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wordflip.core.ui.theme.WordFlipColors

/**
 * 统计四宫格 KPI（REQ-STATS-1）。
 */
@Composable
fun StatsQuadGrid(
    masteredCount: Int,
    streakDays: Int,
    quizAccuracyPercent: Int,
    totalStudyDays: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsKpiCell(
                value = masteredCount.toString(),
                label = "已掌握单词",
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            StatsKpiCell(
                value = streakDays.toString(),
                label = "连续打卡",
                valueColor = WordFlipColors.extra.warning,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsKpiCell(
                value = "$quizAccuracyPercent%",
                label = "测验正确率",
                valueColor = WordFlipColors.extra.success,
                modifier = Modifier.weight(1f),
            )
            StatsKpiCell(
                value = totalStudyDays.toString(),
                label = "累计学习天",
                valueColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatsKpiCell(
    value: String,
    label: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = valueColor,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
