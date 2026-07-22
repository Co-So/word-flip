package com.wordflip.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wordflip.core.model.stats.HeatmapDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 单周学习汇总，供柱状图展示 */
data class WeekActivity(
    val weekStart: LocalDate,
    val activeDays: Int,
)

/**
 * 近 12 周学习柱状图（REQ-STATS-2 可读性优化）：柱高 = 当周有学习记录的天数。
 */
@Composable
fun StudyActivityChart(
    days: List<HeatmapDay>,
    modifier: Modifier = Modifier,
) {
    val weeks = remember(days) { buildWeeklyActivity(days).takeLast(12) }
    val totalActiveDays = remember(days) { days.count { it.level > 0 } }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("M/d") }
    val chartDescription = remember(weeks, totalActiveDays) {
        buildString {
            append("近三个月学习活动，共学习 $totalActiveDays 天。")
            if (weeks.isNotEmpty()) {
                append("每周活跃天数：")
                append(
                    weeks.joinToString(separator = "；") { week ->
                        "${monthFormatter.format(week.weekStart)} 开始的一周 ${week.activeDays} 天"
                    },
                )
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = chartDescription
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "近 3 个月共学习 $totalActiveDays 天",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "柱高表示当周有学习记录的天数（0～7 天）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                weeks.forEach { week ->
                    WeekBar(
                        week = week,
                        label = monthFormatter.format(week.weekStart),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            ActivityLegendRow()
        }
    }
}

@Composable
private fun WeekBar(
    week: WeekActivity,
    label: String,
    modifier: Modifier = Modifier,
) {
    val barFraction = (week.activeDays / 7f).coerceIn(0f, 1f)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = week.activeDays.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .width(20.dp)
                .height((72 * barFraction).dp.coerceAtLeast(if (week.activeDays > 0) 6.dp else 4.dp))
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(barColor(week.activeDays)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActivityLegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendChip("0 天", barColor(0))
        LegendChip("1～2 天", barColor(1))
        LegendChip("3～4 天", barColor(3))
        LegendChip("5～7 天", barColor(6))
    }
}

@Composable
private fun LegendChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .height(10.dp)
                .width(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun barColor(activeDays: Int): androidx.compose.ui.graphics.Color {
    val primary = MaterialTheme.colorScheme.primary
    return when {
        activeDays <= 0 -> MaterialTheme.colorScheme.surfaceVariant
        activeDays <= 2 -> primary.copy(alpha = 0.35f)
        activeDays <= 4 -> primary.copy(alpha = 0.65f)
        else -> primary
    }
}

private fun buildWeeklyActivity(days: List<HeatmapDay>): List<WeekActivity> {
    if (days.isEmpty()) return emptyList()
    val parsed = days.mapNotNull { day ->
        runCatching { LocalDate.parse(day.date) to day.level }.getOrNull()
    }
    if (parsed.isEmpty()) return emptyList()

    val grouped = linkedMapOf<LocalDate, Int>()
    parsed.forEach { (date, level) ->
        // 以周一为周起始，便于用户理解
        val weekStart = date.minusDays((date.dayOfWeek.value - 1).toLong())
        grouped[weekStart] = (grouped[weekStart] ?: 0) + if (level > 0) 1 else 0
    }
    return grouped.map { (weekStart, activeDays) ->
        WeekActivity(weekStart = weekStart, activeDays = activeDays)
    }
}
