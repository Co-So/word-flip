package com.wordflip.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 扩展语义色，补充 M3 ColorScheme 未内置的 success / warning（MASTER §2.2）。
 */
@Immutable
data class WordFlipExtraColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
)

val LightExtraColors = WordFlipExtraColors(
    success = AppleLightSuccess,
    successContainer = Color(0xFFE0F5EE),
    warning = Color(0xFFC47D00),
    warningContainer = Color(0xFFFEF3DC),
)

val DarkExtraColors = WordFlipExtraColors(
    success = AppleDarkSuccess,
    successContainer = Color(0xFF123528),
    warning = Color(0xFFFFB95A),
    warningContainer = Color(0xFF3D2E14),
)

val LocalWordFlipExtraColors = staticCompositionLocalOf { LightExtraColors }

/** 便捷访问扩展语义色 */
object WordFlipColors {
    val extra: WordFlipExtraColors
        @androidx.compose.runtime.Composable
        get() = LocalWordFlipExtraColors.current
}
