package com.wordflip.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.wordflip.core.model.media.StainConfig
import com.wordflip.core.model.media.StainGenerator
import com.wordflip.core.model.media.StainItem
import com.wordflip.core.model.media.StainType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Long 取模可能为负，转色板索引时必须非负 */
private fun Long.modIndex(size: Int): Int =
    mod(size.toLong()).toInt()

/**
 * 卡片正面污渍纹理层（REQ-STUDY-4、REQ-STAIN-2）；支持多类型 Canvas 渲染。
 */
@Composable
fun StainOverlay(
    wordKey: String,
    seed: Long,
    config: StainConfig?,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
) {
    if (hidden) return

    val resolved = remember(wordKey, seed, config) {
        StainGenerator.resolveConfig(wordKey, seed, config)
    }
    val isDark = isSystemInDarkTheme()
    val agingFactor = resolved.aging / 100f

    Canvas(modifier = modifier.fillMaxSize()) {
        for (stain in resolved.stains) {
            val cx = stain.x * size.width
            val cy = stain.y * size.height
            rotate(stain.rotation * 57.2958f, pivot = Offset(cx, cy)) {
                when (stain.type) {
                    StainType.COFFEE -> drawCoffee(cx, cy, stain, isDark, agingFactor)
                    StainType.INK -> drawInk(cx, cy, stain, isDark, agingFactor)
                    StainType.HIGHLIGHT -> drawHighlight(cx, cy, stain, agingFactor)
                    StainType.CRAYON -> drawCrayon(cx, cy, stain, isDark, agingFactor)
                    StainType.RANDOM_LINE -> drawRandomLine(stain, agingFactor)
                }
            }
        }
    }
}

private fun DrawScope.drawCoffee(
    cx: Float,
    cy: Float,
    stain: StainItem,
    isDark: Boolean,
    aging: Float,
) {
    val base = if (isDark) Color(0xFF8B6914) else Color(0xFF6B4E0A)
    val alpha = (0.18f + stain.intensity * 0.22f) * (1f - aging * 0.15f)
    drawBlob(cx, cy, stain.size, stain.seed, base.copy(alpha = alpha))
    drawBlob(cx + 4f, cy + 3f, stain.size * 0.6f, stain.seed + 1, base.copy(alpha = alpha * 0.7f))
}

private fun DrawScope.drawInk(
    cx: Float,
    cy: Float,
    stain: StainItem,
    isDark: Boolean,
    aging: Float,
) {
    val color = if (isDark) Color(0xFF3A3A48) else Color(0xFF1A1A28)
    val alpha = (0.25f + stain.intensity * 0.3f) * (1f - aging * 0.12f)
    drawBlob(cx, cy, stain.size * 0.8f, stain.seed, color.copy(alpha = alpha))
}

private fun DrawScope.drawHighlight(
    cx: Float,
    cy: Float,
    stain: StainItem,
    aging: Float,
) {
    val colors = listOf(
        Color(0xFFFFEB3B),
        Color(0xFF76FF03),
        Color(0xFFFF80AB),
    )
    val color = colors[stain.seed.modIndex(colors.size)]
    val alpha = (0.35f + stain.intensity * 0.25f) * (1f - aging * 0.1f)
    val w = stain.size * 1.8f
    val h = stain.size * 0.5f
    drawRect(
        color = color.copy(alpha = alpha),
        topLeft = Offset(cx - w / 2f, cy - h / 2f),
        size = androidx.compose.ui.geometry.Size(w, h),
        blendMode = BlendMode.Multiply,
    )
}

private fun DrawScope.drawCrayon(
    cx: Float,
    cy: Float,
    stain: StainItem,
    isDark: Boolean,
    aging: Float,
) {
    val palette = if (isDark) {
        listOf(Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784))
    } else {
        listOf(Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFF66BB6A))
    }
    val color = palette[stain.seed.modIndex(palette.size)]
    val alpha = (0.2f + stain.intensity * 0.25f) * (1f - aging * 0.1f)
    val random = Random(stain.seed)
    val path = Path()
    var x = cx - stain.size
    var y = cy
    path.moveTo(x, y)
    repeat(8) {
        x += stain.size * 0.25f
        y += (random.nextFloat() - 0.5f) * stain.size * 0.3f
        path.lineTo(x, y)
    }
    drawPath(path, color.copy(alpha = alpha), style = Stroke(width = stain.size * 0.15f))
}

private fun DrawScope.drawRandomLine(stain: StainItem, aging: Float) {
    val random = Random(stain.seed)
    val alpha = (0.15f + stain.intensity * 0.2f) * (1f - aging * 0.1f)
    val path = Path()
    var x = random.nextFloat() * size.width * 0.3f
    var y = random.nextFloat() * size.height * 0.3f + size.height * 0.1f
    path.moveTo(x, y)
    repeat(5 + random.nextInt(4)) {
        x += size.width * (0.08f + random.nextFloat() * 0.12f)
        y += (random.nextFloat() - 0.5f) * size.height * 0.15f
        path.lineTo(x.coerceIn(0f, size.width), y.coerceIn(0f, size.height))
    }
    drawPath(
        path,
        Color(0xFF5D4037).copy(alpha = alpha),
        style = Stroke(width = 1.5f + stain.intensity * 2f),
    )
}

private fun DrawScope.drawBlob(
    cx: Float,
    cy: Float,
    radius: Float,
    seed: Long,
    color: Color,
) {
    val random = Random(seed)
    val path = Path()
    val points = 6 + random.nextInt(4)
    for (p in 0 until points) {
        val angle = (2 * Math.PI * p / points).toFloat()
        val r = radius * (0.65f + random.nextFloat() * 0.45f)
        val x = cx + cos(angle) * r
        val y = cy + sin(angle) * r
        if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Fill)
}
