package com.wordflip.feature.study

import android.content.Context
import android.provider.Settings
import com.wordflip.core.model.study.WordCard
import kotlin.random.Random

/**
 * REQ-STUDY-11：打乱动画阶段。
 * Stacking → 收拢成叠；Dealing → 发牌到新格子。
 */
enum class ShufflePhase {
    None,
    Stacking,
    Dealing,
}

/** 2 列网格尺寸，供收拢/发牌位移计算 */
data class ShuffleGridSpec(
    val cardWidthPx: Float,
    val cardHeightPx: Float,
    val gapPx: Float,
    val columnCount: Int = 2,
)

/** 打乱开始时视口锚点：牌堆收拢到当前可见区域中心（content 坐标） */
data class ShuffleViewportAnchor(
    val centerXPx: Float,
    val centerYPx: Float,
    val contentPaddingLeftPx: Float,
    val contentPaddingTopPx: Float,
    /** 捕获瞬间 LazyGrid 的 viewportStartOffset，用于发牌阶段重排后恢复滚动位置 */
    val viewportStartOffsetPx: Int = 0,
    /** 打乱瞬间按 wordKey 记录的卡片中心（content 坐标） */
    val measuredCentersByWordKey: Map<String, Pair<Float, Float>> = emptyMap(),
    /** 打乱开始时按 index 预计算的卡片中心（content 坐标） */
    val centersByIndexAtStart: Map<Int, Pair<Float, Float>> = emptyMap(),
)

/**
 * 单卡打乱参数：洗牌散开/旋转 + 牌叠微旋转 + 落牌错位。
 * 参考 pukepai.html 的扑克洗牌效果。
 */
data class ShuffleMotion(
    val stackRotation: Float,
    val scatterTx: Float,
    val scatterTy: Float,
    val scatterRotation: Float,
    val dealOffset: Pair<Float, Float>,
) {
    companion object {
        const val STACK_MS = 700
        const val DEAL_MS = 600
        const val STACK_STAGGER_MS = 18
        const val DEAL_STAGGER_MS = 40
        const val BUFFER_MS = 60
        const val REDUCED_MOTION_MS = 180

        fun random(seed: Long): ShuffleMotion {
            val random = Random(seed)
            return ShuffleMotion(
                stackRotation = (random.nextFloat() - 0.5f) * 12f,
                scatterTx = (random.nextFloat() - 0.5f) * 140f,
                scatterTy = (random.nextFloat() - 0.5f) * 140f,
                scatterRotation = (random.nextFloat() - 0.5f) * 720f,
                dealOffset = ((random.nextFloat() - 0.5f) * 16f) to ((random.nextFloat() - 0.5f) * 16f),
            )
        }

        /**
         * 计算卡片收拢/发牌位移。
         * @param cellIndex 当前格子序号（Stacking 用 startIndex，Dealing 用新 index）
         */
        fun gatherOffset(
            cellIndex: Int,
            spec: ShuffleGridSpec,
            anchor: ShuffleViewportAnchor,
            wordKey: String? = null,
        ): Pair<Float, Float> {
            val (itemCenterX, itemCenterY) = itemCenter(
                cellIndex = cellIndex,
                spec = spec,
                anchor = anchor,
                wordKey = wordKey,
            )
            // 牌叠仅用 scale 区分层次，位移目标完全一致，避免堆叠后漂移
            return (anchor.centerXPx - itemCenterX) to (anchor.centerYPx - itemCenterY)
        }

        /**
         * 发牌终点位移：布局仍停在 startIndex，graphicsLayer 偏移至 dealIndex 格心。
         * 动画期间不重排 LazyGrid，避免发牌阶段 relayout 卡顿。
         */
        fun dealEndOffset(
            startIndex: Int,
            dealIndex: Int,
            spec: ShuffleGridSpec,
            anchor: ShuffleViewportAnchor,
            wordKey: String? = null,
        ): Pair<Float, Float> {
            val (startX, startY) = itemCenter(startIndex, spec, anchor, wordKey)
            val (dealX, dealY) = itemCenter(dealIndex, spec, anchor)
            return (dealX - startX) to (dealY - startY)
        }

        /** 卡片格心（content 坐标） */
        fun itemCenter(
            cellIndex: Int,
            spec: ShuffleGridSpec,
            anchor: ShuffleViewportAnchor,
            wordKey: String? = null,
        ): Pair<Float, Float> = resolveItemCenter(cellIndex, spec, anchor, wordKey)

        private fun resolveItemCenter(
            cellIndex: Int,
            spec: ShuffleGridSpec,
            anchor: ShuffleViewportAnchor,
            wordKey: String?,
        ): Pair<Float, Float> {
            wordKey?.let { key ->
                anchor.measuredCentersByWordKey[key]?.let { return it }
            }
            anchor.centersByIndexAtStart[cellIndex]?.let { return it }
            return estimatedItemCenter(cellIndex, spec, anchor)
        }

        /** 打乱开始时预计算全部卡片中心，避免第二次打乱公式与实测偏差 */
        fun precomputeCentersByIndex(
            cardCount: Int,
            spec: ShuffleGridSpec,
            anchor: ShuffleViewportAnchor,
        ): Map<Int, Pair<Float, Float>> {
            return (0 until cardCount).associateWith { idx ->
                estimatedItemCenter(idx, spec, anchor)
            }
        }

        /** 不可见项：按网格行列推算中心（content 坐标） */
        private fun estimatedItemCenter(
            gridIndex: Int,
            spec: ShuffleGridSpec,
            anchor: ShuffleViewportAnchor,
        ): Pair<Float, Float> {
            val col = gridIndex % spec.columnCount
            val row = gridIndex / spec.columnCount
            val cellW = spec.cardWidthPx + spec.gapPx
            val cellH = spec.cardHeightPx + spec.gapPx
            val x = anchor.contentPaddingLeftPx + col * cellW + spec.cardWidthPx / 2f
            val y = anchor.contentPaddingTopPx + row * cellH + spec.cardHeightPx / 2f
            return x to y
        }

        const val SETTLE_MS = 120L
        /** 收拢完成后停留，再重排发牌，模仿 pukepai.html 的“牌叠落定”节奏 */
        const val STACK_HOLD_MS = 150L

        fun stackDurationMs(cardCount: Int, reduceMotion: Boolean): Long {
            if (cardCount <= 0) return 0L
            if (reduceMotion) return REDUCED_MOTION_MS.toLong()
            return (cardCount - 1) * STACK_STAGGER_MS + STACK_MS + BUFFER_MS.toLong()
        }

        fun dealDurationMs(cardCount: Int, reduceMotion: Boolean): Long {
            if (cardCount <= 0) return 0L
            if (reduceMotion) return REDUCED_MOTION_MS.toLong()
            return (cardCount - 1) * DEAL_STAGGER_MS + DEAL_MS + BUFFER_MS.toLong()
        }

        fun isReduceMotionEnabled(context: Context): Boolean {
            return runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) == 0f
            }.getOrDefault(false)
        }

        /**
         * REQ-STUDY-11：立即计算每卡视觉目标。
         * 逻辑重排瞬间完成，返回 wordKey -> (startCenter, targetCenter)，
         * 供 ShuffleVisual 驱动动画，彻底解耦 LazyGrid 索引。
         */
        fun computeVisualTargets(
            preShuffle: List<WordCard>,
            postShuffle: List<WordCard>,
            anchor: ShuffleViewportAnchor,
            spec: ShuffleGridSpec,
        ): Map<String, Pair<Pair<Float, Float>, Pair<Float, Float>>> {
            val startCenters = preShuffle.mapIndexed { idx, w ->
                w.wordKey to resolveItemCenter(idx, spec, anchor, w.wordKey)
            }.toMap()
            val targetCenters = postShuffle.mapIndexed { idx, w ->
                w.wordKey to resolveItemCenter(idx, spec, anchor, w.wordKey)
            }.toMap()
            return preShuffle.associate { w ->
                val start = startCenters[w.wordKey] ?: estimatedItemCenter(0, spec, anchor)
                val target = targetCenters[w.wordKey] ?: estimatedItemCenter(0, spec, anchor)
                w.wordKey to (start to target)
            }
        }
    }
}
