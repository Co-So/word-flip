package com.wordflip.core.ui.component

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.wordflip.core.model.media.StainConfig
import com.wordflip.core.model.media.StainGenerator
import com.wordflip.core.model.media.StainItem
import com.wordflip.core.model.media.StainNoise
import com.wordflip.core.model.media.StainType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Long 取模可能为负，转索引时必须非负 */
private fun Long.modIndex(size: Int): Int = mod(size.toLong()).toInt()

/**
 * 纯算法污渍像素渲染器：离屏 ARGB 缓冲 + 物理近似模型（咖啡环 / 墨水 metaball / 折痕距离场）。
 * 对齐 REQ-STAIN-2；同一 seed 输出完全一致。
 */
internal object StainBitmapRenderer {

    private const val CACHE_MAX = 72

    private val bitmapCache = object : LinkedHashMap<String, ImageBitmap>(CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
            size > CACHE_MAX
    }

    fun render(
        wordKey: String,
        seed: Long,
        config: StainConfig?,
        width: Int,
        height: Int,
        isDark: Boolean,
    ): ImageBitmap {
        require(width > 0 && height > 0)
        val resolved = StainGenerator.resolveConfig(wordKey, seed, config)
        val cacheKey = buildCacheKey(wordKey, seed, resolved, width, height, isDark)
        synchronized(bitmapCache) {
            bitmapCache[cacheKey]?.let { return it }
        }

        val pixels = IntArray(width * height)
        val aging = resolved.aging / 100f
        val scale = width / 360f

        for (stain in resolved.stains) {
            when (stain.type) {
                StainType.COFFEE -> stampCoffee(pixels, width, height, stain, aging, scale, isDark)
                StainType.INK -> stampInk(pixels, width, height, stain, aging, scale, isDark)
                StainType.HIGHLIGHT -> stampHighlight(pixels, width, height, stain, aging, scale)
                StainType.CRAYON -> stampCrayon(pixels, width, height, stain, aging, scale)
                StainType.RANDOM_LINE -> stampCreases(pixels, width, height, stain, aging)
            }
        }
        applyPaperGrain(pixels, width, height, resolved.seed, aging)

        val image = ImageBitmap(width, height)
        image.asAndroidBitmap().setPixels(pixels, 0, width, 0, 0, width, height)
        synchronized(bitmapCache) {
            bitmapCache[cacheKey] = image
        }
        return image
    }

    private fun buildCacheKey(
        wordKey: String,
        seed: Long,
        config: StainConfig,
        width: Int,
        height: Int,
        isDark: Boolean,
    ): String = buildString {
        append(wordKey).append('|')
        append(seed).append('|')
        append(config.seed).append('|')
        append(config.density).append('|')
        append(config.aging).append('|')
        append(width).append('x').append(height).append('|')
        append(isDark).append('|')
        config.stains.forEach { s ->
            append(s.type.name).append(':').append(s.seed).append(';')
        }
    }

    /** 咖啡环：径向高斯环 + 中心淡染 + 域扭曲边缘 */
    private fun stampCoffee(
        pixels: IntArray,
        width: Int,
        height: Int,
        stain: StainItem,
        aging: Float,
        scale: Float,
        isDark: Boolean,
    ) {
        val cx = stain.x * width
        val cy = stain.y * height
        val maxR = stain.size * 0.55f * scale
        val cosR = cos(stain.rotation.toDouble()).toFloat()
        val sinR = sin(stain.rotation.toDouble()).toFloat()
        val intensity = stain.intensity * (1f - aging * 0.15f)
        val seed = stain.seed

        val light = if (isDark) 0xFFB8956AL else 0xFFC4A574
        val mid = if (isDark) 0xFF8B5E2EL else 0xFF7A4E22
        val dark = if (isDark) 0xFF5C3A18L else 0xFF4A3018

        val margin = (maxR * 1.35f).toInt() + 2
        val x0 = (cx - margin).toInt().coerceAtLeast(0)
        val y0 = (cy - margin).toInt().coerceAtLeast(0)
        val x1 = (cx + margin).toInt().coerceAtMost(width - 1)
        val y1 = (cy + margin).toInt().coerceAtMost(height - 1)

        var y = y0
        while (y <= y1) {
            var x = x0
            while (x <= x1) {
                val (lx, ly) = toLocal(x.toFloat(), y.toFloat(), cx, cy, cosR, sinR)
                val (wx, wy) = StainNoise.domainWarp(lx / maxR, ly / maxR, seed, 0.12f)
                val r = hypot(wx * maxR, wy * maxR) / maxR
                if (r <= 1.35f) {
                    // 咖啡环：外晕 + 主环 + 中心淡渍
                    val halo = StainNoise.gaussian(r, 1.05f, 0.28f) * 0.35f
                    val ring = StainNoise.gaussian(r, 0.78f, 0.14f) * 0.85f
                    val innerRing = StainNoise.gaussian(r, 0.55f, 0.1f) * 0.35f
                    val center = (1f - StainNoise.smoothstep(0f, 0.32f, r)) * 0.18f
                    var alpha = (halo + ring + innerRing + center) * intensity
                    // 纸纤维留白
                    val fiber = StainNoise.fractalNoise(x * 0.35f, y * 0.35f, seed + 500L, 2)
                    if (fiber > 0.93f) alpha *= 0.35f
                    // 环区偏深、外晕偏浅
                    val color = when {
                        ring > 0.45f -> lerpColor(mid, dark, (ring - 0.45f) / 0.55f)
                        halo > 0.15f -> lerpColor(light, mid, halo / 0.35f)
                        else -> lerpColor(light, mid, center * 3f)
                    }
                    alphaOver(pixels, width, x, y, color, alpha.coerceIn(0f, 0.75f))
                }
                x++
            }
            y++
        }
    }

    /** 墨水：Metaball 高斯场叠加 + 二次触须 */
    private fun stampInk(
        pixels: IntArray,
        width: Int,
        height: Int,
        stain: StainItem,
        aging: Float,
        scale: Float,
        isDark: Boolean,
    ) {
        val cx = stain.x * width
        val cy = stain.y * height
        val maxR = stain.size * 0.5f * scale
        val cosR = cos(stain.rotation.toDouble()).toFloat()
        val sinR = sin(stain.rotation.toDouble()).toFloat()
        val intensity = stain.intensity * (1f - aging * 0.12f)
        val seed = stain.seed
        val spotMode = StainNoise.seededRandom(seed) < 0.5f

        val inkColors = longArrayOf(0xFF0F141EL, 0xFF141E3CL, 0xFF320F14L)
        val baseColor = inkColors[StainNoise.randInt(0, inkColors.lastIndex, seed + 2L)]

        // metaball 中心点
        val blobCount = if (spotMode) StainNoise.randInt(2, 5, seed + 3L) else StainNoise.randInt(8, 18, seed + 3L)
        val blobs = Array(blobCount) { i ->
            val angle = StainNoise.randRange(0f, (PI * 2).toFloat(), seed + i * 11L + 4L)
            val dist = if (spotMode) {
                StainNoise.randRange(0f, maxR * 0.35f, seed + i * 11L + 5L)
            } else {
                StainNoise.randRange(0f, maxR * 0.95f, seed + i * 11L + 5L)
            }
            val sigma = StainNoise.randRange(maxR * 0.12f, maxR * 0.35f, seed + i * 11L + 6L)
            Blob(cx + cos(angle) * dist, cy + sin(angle) * dist, sigma)
        }

        val margin = (maxR * 1.4f).toInt() + 2
        val x0 = (cx - margin).toInt().coerceAtLeast(0)
        val y0 = (cy - margin).toInt().coerceAtLeast(0)
        val x1 = (cx + margin).toInt().coerceAtMost(width - 1)
        val y1 = (cy + margin).toInt().coerceAtMost(height - 1)
        val threshold = if (spotMode) 0.45f else 0.28f

        var y = y0
        while (y <= y1) {
            var x = x0
            while (x <= x1) {
                val (lx, ly) = toLocal(x.toFloat(), y.toFloat(), cx, cy, cosR, sinR)
                var field = 0f
                for (b in blobs) {
                    val dx = lx - (b.x - cx)
                    val dy = ly - (b.y - cy)
                    val d2 = dx * dx + dy * dy
                    field += kotlin.math.exp(-d2 / (2f * b.sigma * b.sigma))
                }
                if (field > threshold * 0.5f) {
                    val alpha = StainNoise.smoothstep(threshold * 0.5f, threshold + 0.35f, field) * intensity
                    val edgeNoise = StainNoise.fractalNoise(lx * 0.08f, ly * 0.08f, seed, 2) * 0.15f
                    val finalAlpha = (alpha * (0.85f + edgeNoise)).coerceIn(0f, 0.88f)
                    alphaOver(pixels, width, x, y, baseColor, finalAlpha)
                }
                x++
            }
            y++
        }

        // 墨水触须（仅 spot 模式）
        if (spotMode) {
            val tendrils = StainNoise.randInt(3, 7, seed + 80L)
            repeat(tendrils) { i ->
                val angle = StainNoise.randRange(0f, (PI * 2).toFloat(), seed + i * 13L + 81L)
                val len = StainNoise.randRange(maxR * 0.35f, maxR * 1.0f, seed + i * 13L + 82L)
                val steps = (len / 1.5f).toInt().coerceAtLeast(4)
                repeat(steps) { s ->
                    val t = s.toFloat() / steps
                    val wobble = StainNoise.fractalNoise(t * 6f, i.toFloat(), seed + i * 7L, 2) * maxR * 0.08f
                    val px = cx + cos(angle) * maxR * 0.35f * t + cos(angle + 0.5f) * wobble
                    val py = cy + sin(angle) * maxR * 0.35f * t + sin(angle + 0.5f) * wobble
                    stampDisc(pixels, width, height, px, py, 1.2f + (1f - t) * 2f, baseColor, 0.35f * (1f - t) * intensity)
                }
            }
        }
    }

    /** 荧光笔：沿三次贝塞尔路径压笔，中间厚、两端薄 */
    private fun stampHighlight(
        pixels: IntArray,
        width: Int,
        height: Int,
        stain: StainItem,
        aging: Float,
        scale: Float,
    ) {
        val seed = stain.seed
        val intensity = stain.intensity * (1f - aging * 0.1f)
        val cx = stain.x * width
        val cy = stain.y * height
        val size = stain.size * scale
        val colors = intArrayOf(0xFFFFEB3C.toInt(), 0xFFFF78B4.toInt(), 0xFFFFA032.toInt(), 0xFF64DC78.toInt())
        val color = colors[seed.modIndex(colors.size)]
        val nLines = StainNoise.randInt(2, 5, seed + 1L)

        repeat(nLines) { i ->
            val yOffset = StainNoise.randRange(-size * 0.25f, size * 0.25f, seed + i * 10L + 3L)
            val lineLen = StainNoise.randRange(size * 0.45f, size * 1.25f, seed + i * 10L + 4L)
            val startX = cx - lineLen / 2f
            val startY = cy + yOffset
            val cp1x = startX + lineLen * 0.3f
            val cp1y = startY + StainNoise.randRange(-size * 0.06f, size * 0.06f, seed + i * 10L + 5L)
            val cp2x = startX + lineLen * 0.7f
            val cp2y = startY + StainNoise.randRange(-size * 0.06f, size * 0.06f, seed + i * 10L + 6L)
            val endX = startX + lineLen
            val endY = startY

            val steps = 48
            var s = 0
            while (s < steps) {
                if (StainNoise.seededRandom(seed + i * 50L + s) < 0.06f) {
                    s += 2
                    continue
                }
                val t = s.toFloat() / steps
                val (px, py) = cubicBezier(startX, startY, cp1x, cp1y, cp2x, cp2y, endX, endY, t)
                val pressure = sin(t * PI.toFloat()) * (0.7f + StainNoise.fractalNoise(t * 10f, i.toFloat(), seed + i * 50L, 2) * 0.5f)
                val radius = (size * 0.045f * pressure).coerceAtLeast(1.2f)
                val alpha = 0.42f * intensity * pressure
                stampDisc(pixels, width, height, px, py, radius, color.toLong() and 0xFFFFFFFFL, alpha)
                s++
            }
        }
    }

    /** 蜡笔：定向刮擦条纹 + 区域颗粒 */
    private fun stampCrayon(
        pixels: IntArray,
        width: Int,
        height: Int,
        stain: StainItem,
        aging: Float,
        scale: Float,
    ) {
        val seed = stain.seed
        val intensity = stain.intensity * (1f - aging * 0.1f)
        val cx = stain.x * width
        val cy = stain.y * height
        val size = stain.size * scale
        val cosR = cos(stain.rotation.toDouble()).toFloat()
        val sinR = sin(stain.rotation.toDouble()).toFloat()
        val palette = longArrayOf(0xFFDC8C8CL, 0xFFB4C88CL, 0xFF8CB4DCL, 0xFFDCB48CL, 0xFFC8A0C8L)
        val color = palette[seed.modIndex(palette.size)]

        // 不规则区域填充
        val regionR = size * 0.42f
        val margin = regionR.toInt() + 2
        val x0 = (cx - margin).toInt().coerceAtLeast(0)
        val y0 = (cy - margin).toInt().coerceAtLeast(0)
        val x1 = (cx + margin).toInt().coerceAtMost(width - 1)
        val y1 = (cy + margin).toInt().coerceAtMost(height - 1)

        var y = y0
        while (y <= y1) {
            var x = x0
            while (x <= x1) {
                val (lx, ly) = toLocal(x.toFloat(), y.toFloat(), cx, cy, cosR, sinR)
                val angle = kotlin.math.atan2(ly, lx)
                val dist = hypot(lx, ly)
                val boundary = regionR * (0.65f + StainNoise.fractalNoise(cos(angle) * 2.5f, sin(angle) * 2.5f, seed, 3) * 0.55f)
                if (dist < boundary) {
                    // 定向刮擦：沿 rotation 方向的条纹
                    val scratch = StainNoise.fractalNoise(lx * 0.15f + ly * 0.05f, ly * 0.12f, seed + 200L, 3)
                    val alpha = (0.15f + scratch * 0.35f) * intensity * (1f - dist / boundary * 0.4f)
                    if (StainNoise.fractalNoise(x * 0.4f, y * 0.4f, seed + 300L, 2) > 0.88f) {
                        // 蜡质留白
                    } else {
                        alphaOver(pixels, width, x, y, color, alpha.coerceIn(0f, 0.55f))
                    }
                }
                x++
            }
            y++
        }

        // 刮痕线
        val scratchCount = StainNoise.randInt(3, 7, seed + 30L)
        repeat(scratchCount) { i ->
            val angle = StainNoise.randRange(0f, (PI * 2).toFloat(), seed + i * 15L + 31L)
            val len = StainNoise.randRange(size * 0.25f, size * 0.65f, seed + i * 15L + 32L)
            val steps = (len / 2f).toInt().coerceAtLeast(3)
            repeat(steps) { s ->
                val t = s.toFloat() / steps
                val px = cx + cos(angle) * len * t
                val py = cy + sin(angle) * len * t
                stampDisc(pixels, width, height, px, py, 1.5f, color, 0.2f * intensity * (1f - t * 0.5f))
            }
        }
    }

    /** 折痕：到线段距离场 + 高光/阴影带 */
    private fun stampCreases(
        pixels: IntArray,
        width: Int,
        height: Int,
        stain: StainItem,
        aging: Float,
    ) {
        val seed = stain.seed
        val intensity = stain.intensity * (1f - aging * 0.1f)
        val nCrease = StainNoise.randInt(2, 5, seed)

        data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

        val segments = buildList {
            repeat(nCrease) { i ->
                val s = seed + i * 100L
                add(
                    Segment(
                        x1 = StainNoise.randRange(0f, width.toFloat(), s),
                        y1 = StainNoise.randRange(0f, height.toFloat(), s + 1L),
                        x2 = StainNoise.randRange(0f, width.toFloat(), s + 2L),
                        y2 = StainNoise.randRange(0f, height.toFloat(), s + 3L),
                    ),
                )
            }
        }

        // 仅扫描折痕附近像素（距离场带宽 ~18px）
        val band = 18f
        for (seg in segments) {
            val minX = minOf(seg.x1, seg.x2).toInt().coerceAtLeast(0)
            val maxX = maxOf(seg.x1, seg.x2).toInt().coerceAtMost(width - 1)
            val minY = minOf(seg.y1, seg.y2).toInt().coerceAtLeast(0)
            val maxY = maxOf(seg.y1, seg.y2).toInt().coerceAtMost(height - 1)
            val pad = band.toInt() + 2
            val x0 = (minX - pad).coerceAtLeast(0)
            val y0 = (minY - pad).coerceAtLeast(0)
            val x1 = (maxX + pad).coerceAtMost(width - 1)
            val y1 = (maxY + pad).coerceAtMost(height - 1)

            var y = y0
            while (y <= y1) {
                var x = x0
                while (x <= x1) {
                    val dist = pointToSegmentDistance(x.toFloat(), y.toFloat(), seg.x1, seg.y1, seg.x2, seg.y2)
                    if (dist < band) {
                        val wobble = StainNoise.fractalNoise(x * 0.12f, y * 0.12f, seed, 2) * 4f
                        val effectiveDist = dist + wobble
                        val t = 1f - effectiveDist / band
                        when {
                            effectiveDist < 1.2f -> alphaOver(pixels, width, x, y, 0xFFFFFCF0L, 0.4f * t * intensity)
                            effectiveDist < 4f -> alphaOver(pixels, width, x, y, 0xFF000000L, 0.18f * t * intensity)
                            else -> alphaOver(pixels, width, x, y, 0xFFFFFFFFL, 0.08f * t * intensity)
                        }
                    }
                    x++
                }
                y++
            }
        }

        // 交叉点高光
        for (i in segments.indices) {
            for (j in i + 1 until segments.size) {
                val a = segments[i]
                val b = segments[j]
                val inter = segmentIntersection(a.x1, a.y1, a.x2, a.y2, b.x1, b.y1, b.x2, b.y2)
                if (inter != null) {
                    stampDisc(pixels, width, height, inter.first, inter.second, 9f, 0xFFFFFCF5L, 0.32f * intensity)
                }
            }
        }
    }

    /** 全局纸纹：低频噪声调制已有 alpha */
    private fun applyPaperGrain(pixels: IntArray, width: Int, height: Int, seed: Long, aging: Float) {
        if (aging <= 0.01f) return
        var i = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val px = pixels[i]
                val a = px ushr 24
                if (a > 0) {
                    val grain = (StainNoise.fractalNoise(x * 0.18f, y * 0.18f, seed + 9000L, 2) - 0.5f) * aging * 18f
                    val newA = (a + grain.toInt()).coerceIn(0, 255)
                    pixels[i] = (px and 0x00FFFFFF) or (newA shl 24)
                }
                x++
                i++
            }
            y++
        }
    }

    private data class Blob(val x: Float, val y: Float, val sigma: Float)

    private fun stampDisc(
        pixels: IntArray,
        width: Int,
        height: Int,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Long,
        alpha: Float,
    ) {
        if (alpha <= 0.01f) return
        val r = radius.toInt() + 1
        val x0 = (cx - r).toInt().coerceAtLeast(0)
        val y0 = (cy - r).toInt().coerceAtLeast(0)
        val x1 = (cx + r).toInt().coerceAtMost(width - 1)
        val y1 = (cy + r).toInt().coerceAtMost(height - 1)
        var y = y0
        while (y <= y1) {
            var x = x0
            while (x <= x1) {
                val d = hypot(x - cx, y - cy)
                if (d <= radius) {
                    val falloff = 1f - (d / radius)
                    alphaOver(pixels, width, x, y, color, alpha * falloff * falloff)
                }
                x++
            }
            y++
        }
    }

    private fun alphaOver(pixels: IntArray, width: Int, x: Int, y: Int, rgb: Long, alpha: Float) {
        if (alpha <= 0.001f) return
        val idx = y * width + x
        val srcA = (alpha * 255f).toInt().coerceIn(0, 255)
        if (srcA == 0) return
        val srcR = ((rgb shr 16) and 0xFF).toInt()
        val srcG = ((rgb shr 8) and 0xFF).toInt()
        val srcB = (rgb and 0xFF).toInt()

        val dest = pixels[idx]
        val destA = dest ushr 24
        val invSrcA = 255 - srcA
        val outA = srcA + destA * invSrcA / 255
        if (outA == 0) return
        val destR = (dest shr 16) and 0xFF
        val destG = (dest shr 8) and 0xFF
        val destB = dest and 0xFF
        val outR = (srcR * srcA + destR * destA * invSrcA / 255) / outA
        val outG = (srcG * srcA + destG * destA * invSrcA / 255) / outA
        val outB = (srcB * srcA + destB * destA * invSrcA / 255) / outA
        pixels[idx] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    private fun lerpColor(from: Long, to: Long, t: Float): Long {
        val u = t.coerceIn(0f, 1f)
        val r = (((from shr 16) and 0xFF) * (1 - u) + ((to shr 16) and 0xFF) * u).toInt()
        val g = (((from shr 8) and 0xFF) * (1 - u) + ((to shr 8) and 0xFF) * u).toInt()
        val b = ((from and 0xFF) * (1 - u) + (to and 0xFF) * u).toInt()
        return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    private fun toLocal(x: Float, y: Float, cx: Float, cy: Float, cosR: Float, sinR: Float): Pair<Float, Float> {
        val dx = x - cx
        val dy = y - cy
        return dx * cosR + dy * sinR to -dx * sinR + dy * cosR
    }

    private fun cubicBezier(
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float, t: Float,
    ): Pair<Float, Float> {
        val u = 1f - t
        val x = u * u * u * x0 + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * x3
        val y = u * u * u * y0 + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y3
        return x to y
    }

    private fun pointToSegmentDistance(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-6f) return hypot(px - x1, py - y1)
        val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
        val clamped = t.coerceIn(0f, 1f)
        val nx = x1 + clamped * dx
        val ny = y1 + clamped * dy
        return hypot(px - nx, py - ny)
    }

    private fun segmentIntersection(
        x1: Float, y1: Float, x2: Float, y2: Float,
        x3: Float, y3: Float, x4: Float, y4: Float,
    ): Pair<Float, Float>? {
        val dx1 = x2 - x1
        val dy1 = y2 - y1
        val dx2 = x4 - x3
        val dy2 = y4 - y3
        val det = dx1 * dy2 - dx2 * dy1
        if (kotlin.math.abs(det) < 1e-6f) return null
        val t = ((x3 - x1) * dy2 - (y3 - y1) * dx2) / det
        val u = ((x3 - x1) * dy1 - (y3 - y1) * dx1) / det
        if (t < 0f || t > 1f || u < 0f || u > 1f) return null
        return (x1 + t * dx1) to (y1 + t * dy1)
    }
}
