package com.wordflip.core.model.media

import kotlin.random.Random

/**
 * 污渍确定性生成器（REQ-STAIN-1~3）；seed = stableHash(userId+wordKey) 概念，Mock 用 wordKey。
 */
object StainGenerator {

    private val allTypes = StainType.entries
    private val layerOrder = mapOf(
        StainType.COFFEE to 1,
        StainType.INK to 2,
        StainType.HIGHLIGHT to 3,
        StainType.CRAYON to 4,
        StainType.RANDOM_LINE to 5,
    )

    /** 与 FakeStudyData / 后端 P3 stableHash 一致的轻量 hash */
    fun stableSeed(wordKey: String): Long {
        var hash = 0L
        for (ch in wordKey) {
            hash = 31L * hash + ch.code
        }
        return hash
    }

    /**
     * 生成污渍配置；overrideSeed 非空时用于「换一个污渍」。
     */
    fun generate(
        wordKey: String,
        mode: StainMode = StainMode.RANDOM,
        allowedTypes: List<StainType> = allTypes,
        density: Int = 50,
        aging: Int = 20,
        overrideSeed: Long? = null,
    ): StainConfig {
        val types = allowedTypes.ifEmpty { allTypes }
        val baseSeed = overrideSeed ?: stableSeed(wordKey)
        val random = Random(baseSeed)

        val enabledTypes = when (mode) {
            StainMode.SINGLE -> listOf(types.first())
            StainMode.MULTI -> types
            StainMode.RANDOM -> {
                val count = random.nextInt(2, 6)
                types.shuffled(random).take(count)
            }
        }

        val stains = mutableListOf<StainItem>()
        val positions = mutableListOf<Pair<Float, Float>>()
        val minDist = 0.15f

        for (type in enabledTypes) {
            val maxCount = random.nextInt(1, 4)
            val stainCount = maxOf(1, (maxCount * density / 100f).toInt())
            repeat(stainCount) { i ->
                val seed = baseSeed + type.ordinal * 100L + i * 1000L
                val pos = weightedPosition(seed, positions, minDist) ?: return@repeat
                positions += pos
                stains += StainItem(
                    type = type,
                    x = pos.first,
                    y = pos.second,
                    size = randRange(seed + 50, 20f, 55f),
                    rotation = randRange(seed + 51, 0f, (Math.PI * 2).toFloat()),
                    intensity = randRange(seed + 52, 0.5f, 1f),
                    seed = seed,
                    layerOrder = layerOrder[type] ?: 5,
                )
            }
        }

        return StainConfig(
            seed = baseSeed,
            mode = mode,
            density = density,
            aging = aging,
            stains = stains.sortedBy { it.layerOrder },
        )
    }

    /** 从 seed 或已有 config 解析渲染用配置 */
    fun resolveConfig(wordKey: String, seed: Long, config: StainConfig?): StainConfig {
        if (config != null && config.stains.isNotEmpty()) return config
        return generate(wordKey, overrideSeed = seed.takeIf { it != 0L })
    }

    private fun weightedPosition(
        seed: Long,
        existing: List<Pair<Float, Float>>,
        minDist: Float,
    ): Pair<Float, Float>? {
        repeat(12) { attempt ->
            val x = randRange(seed + attempt * 3, 0.12f, 0.88f)
            val y = randRange(seed + attempt * 3 + 1, 0.12f, 0.88f)
            val ok = existing.all { (ex, ey) ->
                val dx = x - ex
                val dy = y - ey
                dx * dx + dy * dy >= minDist * minDist
            }
            if (ok) return x to y
        }
        return null
    }

    private fun randRange(seed: Long, min: Float, max: Float): Float {
        val r = Random(seed)
        return min + r.nextFloat() * (max - min)
    }
}
