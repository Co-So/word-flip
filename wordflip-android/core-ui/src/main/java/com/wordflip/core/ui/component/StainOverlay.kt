package com.wordflip.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.wordflip.core.model.media.StainConfig

/**
 * 卡片正面污渍层：离屏像素渲染 + LRU 缓存（纯算法，无贴图素材）。
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

    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }

        if (widthPx <= 0 || heightPx <= 0) return@BoxWithConstraints

        val bitmap: ImageBitmap = remember(wordKey, seed, config, widthPx, heightPx, isDark) {
            StainBitmapRenderer.render(
                wordKey = wordKey,
                seed = seed,
                config = config,
                width = widthPx,
                height = heightPx,
                isDark = isDark,
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = bitmap,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
        }
    }
}
