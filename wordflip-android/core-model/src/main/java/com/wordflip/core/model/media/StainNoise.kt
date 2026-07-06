package com.wordflip.core.model.media

import kotlin.math.floor

/**
 * 污渍生成/渲染共用的确定性噪声与随机工具（对齐 v5 原型 fractalNoise / seededRandom）。
 */
object StainNoise {

    /** 确定性伪随机 [0, 1) */
    fun seededRandom(seed: Long): Float {
        var t = seed + 0x6D2B79F5L
        t = imul32(t xor (t ushr 15), t or 1L)
        t = t xor (t + imul32(t xor (t ushr 7), t or 61L))
        return ((t xor (t ushr 14)) ushr 0).toInt().toUInt().toFloat() / 4294967296f
    }

    fun randRange(min: Float, max: Float, seed: Long): Float =
        min + seededRandom(seed) * (max - min)

    fun randInt(min: Int, max: Int, seed: Long): Int =
        floor(randRange(min.toFloat(), max + 1f, seed)).toInt()

    /** 简化 Value Noise */
    fun valueNoise(x: Float, y: Float, seed: Long): Float {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val fx = x - ix
        val fy = y - iy
        val ux = smoothstep(fx)
        val uy = smoothstep(fy)
        val a = hash2(ix, iy, seed)
        val b = hash2(ix + 1, iy, seed)
        val c = hash2(ix, iy + 1, seed)
        val d = hash2(ix + 1, iy + 1, seed)
        return a + (b - a) * ux + (c - a) * uy + (a - b - c + d) * ux * uy
    }

    /** 分形噪声，用于有机边缘与纸纹 */
    fun fractalNoise(x: Float, y: Float, seed: Long, octaves: Int = 4): Float {
        var value = 0f
        var amp = 1f
        var freq = 1f
        var maxValue = 0f
        repeat(octaves) { i ->
            value += valueNoise(x * freq, y * freq, seed + i * 100L) * amp
            maxValue += amp
            amp *= 0.5f
            freq *= 2f
        }
        return value / maxValue
    }

    /** 域扭曲：让径向对称图案产生有机偏移 */
    fun domainWarp(x: Float, y: Float, seed: Long, strength: Float = 0.08f): Pair<Float, Float> {
        val wx = fractalNoise(x, y, seed, 3) * 2f - 1f
        val wy = fractalNoise(x + 17.3f, y + 23.7f, seed + 1L, 3) * 2f - 1f
        return (x + wx * strength) to (y + wy * strength)
    }

    /** 高斯分布，用于咖啡环 / 墨水扩散 */
    fun gaussian(x: Float, mu: Float, sigma: Float): Float {
        if (sigma <= 0f) return 0f
        val d = (x - mu) / sigma
        return kotlin.math.exp(-0.5f * d * d)
    }

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

    private fun hash2(x: Int, y: Int, seed: Long): Float {
        var h = seed
        h = imul32(h xor x.toLong(), 0x45D9F3BL)
        h = imul32(h xor y.toLong(), 0x45D9F3BL)
        h = h xor (h ushr 16)
        return (h and 0xFFFFFFFFL).toInt().toUInt().toFloat() / 4294967296f
    }

    /** 模拟 JS Math.imul 32 位乘法 */
    private fun imul32(a: Long, b: Long): Long = (a * b) and 0xFFFFFFFFL
}
