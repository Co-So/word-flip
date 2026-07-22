package com.wordflip.core.ui.apple

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wordflip.core.ui.theme.AppleDarkAccent
import com.wordflip.core.ui.theme.AppleDarkCanvas
import com.wordflip.core.ui.theme.AppleDarkDestructive
import com.wordflip.core.ui.theme.AppleDarkElevatedSurface
import com.wordflip.core.ui.theme.AppleDarkGlass
import com.wordflip.core.ui.theme.AppleDarkGroupedSurface
import com.wordflip.core.ui.theme.AppleDarkPrimaryText
import com.wordflip.core.ui.theme.AppleDarkSecondaryText
import com.wordflip.core.ui.theme.AppleDarkSeparator
import com.wordflip.core.ui.theme.AppleLightAccent
import com.wordflip.core.ui.theme.AppleLightCanvas
import com.wordflip.core.ui.theme.AppleLightDestructive
import com.wordflip.core.ui.theme.AppleLightElevatedSurface
import com.wordflip.core.ui.theme.AppleLightGlass
import com.wordflip.core.ui.theme.AppleLightGroupedSurface
import com.wordflip.core.ui.theme.AppleLightPrimaryText
import com.wordflip.core.ui.theme.AppleLightSecondaryText
import com.wordflip.core.ui.theme.AppleLightSeparator

/** Apple 风格界面的语义色集合，统一适配明暗主题。 */
@Immutable
data class AppleColors(
    val canvas: Color,
    val groupedSurface: Color,
    val elevatedSurface: Color,
    val glass: Color,
    val accent: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val separator: Color,
    val destructive: Color,
)

/** 提供 Apple 风格界面的基础视觉令牌。 */
object AppleUi {
    val colors: AppleColors
        @Composable get() {
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            return if (dark) {
                AppleColors(
                    canvas = AppleDarkCanvas,
                    groupedSurface = AppleDarkGroupedSurface,
                    elevatedSurface = AppleDarkElevatedSurface,
                    glass = AppleDarkGlass,
                    accent = AppleDarkAccent,
                    primaryText = AppleDarkPrimaryText,
                    secondaryText = AppleDarkSecondaryText,
                    separator = AppleDarkSeparator,
                    destructive = AppleDarkDestructive,
                )
            } else {
                AppleColors(
                    canvas = AppleLightCanvas,
                    groupedSurface = AppleLightGroupedSurface,
                    elevatedSurface = AppleLightElevatedSurface,
                    glass = AppleLightGlass,
                    accent = AppleLightAccent,
                    primaryText = AppleLightPrimaryText,
                    secondaryText = AppleLightSecondaryText,
                    separator = AppleLightSeparator,
                    destructive = AppleLightDestructive,
                )
            }
        }
}

/**
 * 承载浮动导航等前景控件的半透明玻璃表面。
 */
@Composable
fun AppleGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = AppleUi.colors.glass,
        shadowElevation = 14.dp,
        tonalElevation = 0.dp,
    ) {
        Box { content() }
    }
}

/**
 * 在按下期间即时缩小控件，并使用无回弹弹簧恢复到静止状态。
 */
@Composable
fun Modifier.applePress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "applePressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
