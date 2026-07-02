package com.wordflip.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import kotlin.math.cos
import kotlin.math.sin

/**
 * 卡片正面污渍纹理层（REQ-STUDY-4、REQ-STUDY-6）；seed 决定形状，仅显示在正面。
 */
@Composable
fun StainOverlay(
    seed: Long,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
) {
    if (hidden) return

    val isDark = isSystemInDarkTheme()
    val opacity = if (isDark) 0.22f else 0.16f
    val stainColor = if (isDark) Color(0xFF8FAF5C) else Color(0xFF6F9038)

    Canvas(modifier = modifier.fillMaxSize()) {
        val random = seededRandom(seed)
        val blobCount = 3 + (random.nextInt(3))
        for (i in 0 until blobCount) {
            val cx = size.width * (0.2f + random.nextFloat() * 0.6f)
            val cy = size.height * (0.15f + random.nextFloat() * 0.55f)
            val radius = size.minDimension * (0.08f + random.nextFloat() * 0.12f)
            val path = Path().apply {
                val points = 6 + random.nextInt(4)
                for (p in 0 until points) {
                    val angle = (2 * Math.PI * p / points).toFloat()
                    val r = radius * (0.7f + random.nextFloat() * 0.5f)
                    val x = cx + cos(angle) * r
                    val y = cy + sin(angle) * r
                    if (p == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(path, stainColor.copy(alpha = opacity), style = Fill)
        }
    }
}

private class SeededRandom(private var state: Long) {
    fun nextInt(bound: Int): Int {
        state = state * 6364136223846793005L + 1L
        return ((state ushr 33) % bound).toInt().coerceAtLeast(0)
    }

    fun nextFloat(): Float = nextInt(10_000) / 10_000f
}

private fun seededRandom(seed: Long) = SeededRandom(seed xor 0x517CC1B727220A95L)
