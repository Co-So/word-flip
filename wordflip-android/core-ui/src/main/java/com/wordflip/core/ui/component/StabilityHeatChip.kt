package com.wordflip.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordflip.core.model.study.MasteryLevel
import com.wordflip.core.model.study.MasterySnapshot
import com.wordflip.core.model.study.WordProgressSnapshot

/**
 * 组详情主展示：稳定性热力以**颜色**为主（5 格色条），文案为辅；薄弱角标可选。
 * 热力优先 [progress.displayHeatLevel]；薄弱角标看展示轨对应 skill 的 level
 *（combined/free 时任一侧 fuzzy/unknown 即显示）。
 */
@Composable
fun StabilityHeatChip(
    mastery: MasterySnapshot,
    modifier: Modifier = Modifier,
    progress: WordProgressSnapshot? = null,
) {
    val isDark = isSystemInDarkTheme()
    val heat = (progress?.displayHeatLevel ?: mastery.heatLevel).coerceIn(0, 4)
    val weakLevel = resolveWeakBadgeLevel(mastery, progress)
    val style = heatStyle(heat, isDark)
    Row(
        modifier = modifier.semantics { contentDescription = "稳定性，${style.label}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 5 格热力条：点亮格数 = heatLevel+1，颜色随档加深
        HeatMeter(heatLevel = heat, isDark = isDark)
        Text(
            text = style.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = style.foreground,
        )
        when (weakLevel) {
            MasteryLevel.FUZZY -> WeakBadge(
                label = "需加强",
                background = if (isDark) Color(0xFF3D2E14) else Color(0xFFFEF3DC),
                foreground = if (isDark) Color(0xFFFFB95A) else Color(0xFF8B5A00),
                icon = Icons.Outlined.HelpOutline,
            )
            MasteryLevel.UNKNOWN -> WeakBadge(
                label = "需重学",
                background = if (isDark) Color(0xFF3D1F1C) else Color(0xFFFDECEA),
                foreground = if (isDark) Color(0xFFFFB4AB) else Color(0xFF9B2919),
                icon = Icons.Outlined.PriorityHigh,
            )
            MasteryLevel.UNLEARNED -> Unit
        }
    }
}

/** 新学习卡链路使用的热力展示；档位由服务端权威计算。 */
@Composable
fun StabilityHeatChip(
    heatLevel: Int,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val heat = heatLevel.coerceIn(0, 4)
    val style = heatStyle(heat, isDark)
    Row(
        modifier = modifier.semantics { contentDescription = "稳定性，${style.label}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeatMeter(heatLevel = heat, isDark = isDark)
        Text(
            text = style.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = style.foreground,
        )
    }
}

/**
 * 薄弱角标：单轨用对应 skill；combined/free 时任一侧为 fuzzy/unknown（unknown 优先）。
 */
fun resolveWeakBadgeLevel(
    mastery: MasterySnapshot,
    progress: WordProgressSnapshot?,
): MasteryLevel {
    if (progress == null) return mastery.level
    return when (progress.heatDisplayMode.lowercase()) {
        "dictation" -> progress.dictation.level
        "choice" -> progress.choice.level
        else -> {
            // combined / free：任一侧薄弱则显示，unknown 优先于 fuzzy
            val levels = listOf(progress.dictation.level, progress.choice.level)
            when {
                MasteryLevel.UNKNOWN in levels -> MasteryLevel.UNKNOWN
                MasteryLevel.FUZZY in levels -> MasteryLevel.FUZZY
                else -> MasteryLevel.UNLEARNED
            }
        }
    }
}

/**
 * 单词行左侧热力色条 + 行底浅色（组详情列表用）。
 */
@Composable
fun StabilityHeatRowBackground(
    heatLevel: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val heat = heatLevel.coerceIn(0, 4)
    val style = heatStyle(heat, isDark)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(style.rowTint)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(style.background),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun HeatMeter(heatLevel: Int, isDark: Boolean) {
    val lit = heatLevel + 1
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 5) {
            val cellHeat = i.coerceAtMost(4)
            val color = if (i < lit) {
                heatStyle(cellHeat, isDark).background
            } else {
                if (isDark) Color(0xFF2C2C2C) else Color(0xFFE8E6E0)
            }
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun WeakBadge(
    label: String,
    background: Color,
    foreground: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = foreground,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = foreground,
            )
        }
    }
}

private data class HeatStyle(
    val label: String,
    val background: Color,
    val foreground: Color,
    val rowTint: Color,
)

private fun heatStyle(heatLevel: Int, isDark: Boolean): HeatStyle = when (heatLevel) {
    1 -> HeatStyle(
        label = "初识",
        background = if (isDark) Color(0xFF5A7A3A) else Color(0xFFD4E4BC),
        foreground = if (isDark) Color(0xFFC5D99A) else Color(0xFF3D4F1C),
        rowTint = if (isDark) Color(0xFF1E2418) else Color(0xFFF5F8EF),
    )
    2 -> HeatStyle(
        label = "巩固中",
        background = if (isDark) Color(0xFF7A9A4A) else Color(0xFF9BB56A),
        foreground = if (isDark) Color(0xFFE8F0D0) else Color(0xFF1A2E0A),
        rowTint = if (isDark) Color(0xFF222A1A) else Color(0xFFEEF4E4),
    )
    3 -> HeatStyle(
        label = "较熟",
        background = if (isDark) Color(0xFF8FB050) else Color(0xFF6F9038),
        foreground = if (isDark) Color(0xFFE8F0D0) else Color(0xFF3D4F1C),
        rowTint = if (isDark) Color(0xFF26301C) else Color(0xFFE6EFDA),
    )
    4 -> HeatStyle(
        label = "很熟",
        background = if (isDark) Color(0xFF0B7B5C) else Color(0xFF0B7B5C),
        foreground = if (isDark) Color(0xFF6DD5A8) else Color(0xFF0B7B5C),
        rowTint = if (isDark) Color(0xFF123528) else Color(0xFFE0F5EE),
    )
    else -> HeatStyle(
        label = "新词",
        background = if (isDark) Color(0xFF4A4A4A) else Color(0xFFEFEDE7),
        foreground = if (isDark) Color(0xFFB0B0B0) else Color(0xFF616161),
        rowTint = if (isDark) Color(0xFF1A1A1A) else Color(0xFFFAF9F6),
    )
}
