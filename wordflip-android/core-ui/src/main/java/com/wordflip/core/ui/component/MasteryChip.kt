package com.wordflip.core.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked as UnlearnedIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 掌握度展示类型，供分组详情等只读场景使用（REQ-STUDY-24：学习页不展示） */
enum class MasteryChipLevel {
    UNLEARNED,
    FUZZY,
    UNKNOWN,
}

/**
 * 掌握度三态只读 Chip（MASTER §2.3）：颜色 + 文案 + 图标。
 */
@Composable
fun MasteryChip(
    level: MasteryChipLevel,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val style = masteryStyle(level, isDark)
    Surface(
        modifier = modifier.semantics { contentDescription = "掌握度，${style.label}" },
        shape = RoundedCornerShape(8.dp),
        color = style.background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = style.foreground,
            )
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                color = style.foreground,
            )
        }
    }
}

private data class MasteryStyle(
    val label: String,
    val background: Color,
    val foreground: Color,
    val icon: ImageVector,
)

private fun masteryStyle(level: MasteryChipLevel, isDark: Boolean): MasteryStyle {
    return when (level) {
        MasteryChipLevel.UNLEARNED -> MasteryStyle(
            label = "未学习",
            background = if (isDark) Color(0xFF3A3A3A) else Color(0xFFF5F5F5),
            foreground = if (isDark) Color(0xFFB0B0B0) else Color(0xFF616161),
            icon = Icons.Outlined.RadioButtonUnchecked,
        )
        MasteryChipLevel.FUZZY -> MasteryStyle(
            label = "模糊",
            background = if (isDark) Color(0xFF3D2E14) else Color(0xFFFEF3DC),
            foreground = if (isDark) Color(0xFFFFB95A) else Color(0xFF8B5A00),
            icon = Icons.Outlined.HelpOutline,
        )
        MasteryChipLevel.UNKNOWN -> MasteryStyle(
            label = "不认识",
            background = if (isDark) Color(0xFF3D1F1C) else Color(0xFFFDECEA),
            foreground = if (isDark) Color(0xFFFFB4AB) else Color(0xFF9B2919),
            icon = Icons.Outlined.PriorityHigh,
        )
    }
}
