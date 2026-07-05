package com.wordflip.feature.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

private val DealEasing = CubicBezierEasing(0.2f, 0f, 0.3f, 1f)

/** 洗牌：0→0.35 散开；0.35→0.5 收拢成叠；0.5→0.6 停留；0.6→1 发牌 */
private const val SCATTER_PROGRESS = 0.35f
private const val GATHER_PROGRESS = 0.5f
private const val DEAL_START_PROGRESS = 0.6f

/** 单帧打乱变换（translation 单位 px，全程 alpha=1 避免空白） */
private data class CardTransform(
    val tx: Float,
    val ty: Float,
    val rotation: Float,
    val scale: Float,
)

/**
 * REQ-STUDY-11：扑克牌式打乱——散开 → 收拢成叠 → 发牌到新位置；参考 pukepai.html。
 * 视觉层直接使用 wordKey 定位起始/目标中心，彻底解耦索引。
 */
@Composable
fun Modifier.shuffleCardMotion(
    wordKey: String,
    phase: ShufflePhase,
    shuffleEpoch: Int,
    index: Int,
    motion: ShuffleMotion?,
    gridSpec: ShuffleGridSpec?,
    viewportAnchor: ShuffleViewportAnchor?,
    shuffleSettling: Boolean,
    reduceMotion: Boolean,
    dealStartOffset: Pair<Float, Float>? = null, // Dealing 开始时的补偿偏移（已打乱列表下的视觉中心）
): Modifier {
    val canAnimate = motion != null && gridSpec != null && viewportAnchor != null &&
        phase != ShufflePhase.None

    if (!canAnimate) {
        if (shuffleSettling) {
            return this.graphicsLayer {
                translationX = 0f
                translationY = 0f
                rotationZ = 0f
                scaleX = 1f
                scaleY = 1f
                alpha = 1f
                clip = false
            }
        }
        return this
    }

    // 收拢阶段：从当前布局位置飞向视口中心
    val stackGather = ShuffleMotion.gatherOffset(
        cellIndex = index,
        spec = gridSpec!!,
        anchor = viewportAnchor!!,
        wordKey = wordKey,
    )
    // 单一 Animatable 0→1 连续动画
    val animatable = remember(wordKey, shuffleEpoch) { Animatable(0f) }

    val scatterDuration = if (reduceMotion) ShuffleMotion.REDUCED_MOTION_MS else (ShuffleMotion.STACK_MS / 2)
    val gatherDuration = if (reduceMotion) ShuffleMotion.REDUCED_MOTION_MS else (ShuffleMotion.STACK_MS / 2)
    val dealDuration = if (reduceMotion) ShuffleMotion.REDUCED_MOTION_MS else ShuffleMotion.DEAL_MS
    val stackStagger = if (reduceMotion) 0 else ShuffleMotion.STACK_STAGGER_MS * index
    val dealStagger = if (reduceMotion) 0 else ShuffleMotion.DEAL_STAGGER_MS * index + (index % 7) * 5 // jitter

    LaunchedEffect(shuffleEpoch) {
        animatable.snapTo(0f)
        // 0→0.35：散开并旋转
        animatable.animateTo(
            targetValue = SCATTER_PROGRESS,
            animationSpec = tween(
                durationMillis = scatterDuration,
                delayMillis = stackStagger,
                easing = FastOutSlowInEasing,
            ),
        )
        // 0.35→0.5：收拢成叠
        animatable.animateTo(
            targetValue = GATHER_PROGRESS,
            animationSpec = tween(
                durationMillis = gatherDuration,
                easing = FastOutSlowInEasing,
            ),
        )
        // 0.5→0.6：停留
        if (ShuffleMotion.STACK_HOLD_MS > 0) {
            kotlinx.coroutines.delay(ShuffleMotion.STACK_HOLD_MS)
        }
        // 0.6→1.0：发牌
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = dealDuration,
                delayMillis = dealStagger,
                easing = DealEasing,
            ),
        )
    }

    val deckLayer = (index % 12).toFloat()
    val stackedScale = 0.88f - deckLayer * 0.005f
    val progress = animatable.value
    val transform = when {
        progress <= SCATTER_PROGRESS -> {
            val p = (progress / SCATTER_PROGRESS).coerceIn(0f, 1f)
            scatterTransform(p, motion!!)
        }
        progress <= GATHER_PROGRESS -> {
            val p = ((progress - SCATTER_PROGRESS) / (GATHER_PROGRESS - SCATTER_PROGRESS)).coerceIn(0f, 1f)
            gatherTransform(
                progress = p,
                scatterTx = motion!!.scatterTx,
                scatterTy = motion.scatterTy,
                scatterRotation = motion.scatterRotation,
                gatherTx = stackGather.first,
                gatherTy = stackGather.second,
                motion = motion,
                deckLayer = deckLayer,
            )
        }
        progress <= DEAL_START_PROGRESS -> {
            // 停留：保持牌叠中心
            CardTransform(
                tx = stackGather.first,
                ty = stackGather.second,
                rotation = motion!!.stackRotation,
                scale = stackedScale,
            )
        }
        else -> {
            // 发牌：从补偿偏移飞到最终布局位置 (0,0)
            val startTx = dealStartOffset?.first ?: 0f
            val startTy = dealStartOffset?.second ?: 0f
            val p = ((progress - DEAL_START_PROGRESS) / (1f - DEAL_START_PROGRESS)).coerceIn(0f, 1f)
            dealTransform(
                progress = p,
                stackTx = startTx,
                stackTy = startTy,
                endTx = 0f,
                endTy = 0f,
                motion = motion!!,
                stackedScale = stackedScale,
            )
        }
    }

    return this
        .zIndex(deckLayer)
        .graphicsLayer {
            translationX = transform.tx
            translationY = transform.ty
            rotationZ = transform.rotation
            scaleX = transform.scale
            scaleY = transform.scale
            alpha = 1f
            clip = false
        }
}

/** 散开：从当前位置随机飞出并旋转，参考 pukepai.html 洗牌 */
private fun scatterTransform(
    progress: Float,
    motion: ShuffleMotion,
): CardTransform {
    val p = progress.coerceIn(0f, 1f)
    return CardTransform(
        tx = motion.scatterTx * p,
        ty = motion.scatterTy * p,
        rotation = motion.scatterRotation * p,
        scale = 1f,
    )
}

/** 收拢：从散开位置滑向视口中心叠成牌堆 */
private fun gatherTransform(
    progress: Float,
    scatterTx: Float,
    scatterTy: Float,
    scatterRotation: Float,
    gatherTx: Float,
    gatherTy: Float,
    motion: ShuffleMotion,
    deckLayer: Float,
): CardTransform {
    val p = progress.coerceIn(0f, 1f)
    return CardTransform(
        tx = lerp(scatterTx, gatherTx, p),
        ty = lerp(scatterTy, gatherTy, p),
        rotation = lerp(scatterRotation, motion.stackRotation, p),
        scale = lerp(1f, 0.88f - deckLayer * 0.005f, p),
    )
}

/** 发牌：从牌叠中心滑至目标格，带轻微落牌错位 */
private fun dealTransform(
    progress: Float,
    stackTx: Float,
    stackTy: Float,
    endTx: Float,
    endTy: Float,
    motion: ShuffleMotion,
    stackedScale: Float,
): CardTransform {
    val p = progress.coerceIn(0f, 1f)
    return CardTransform(
        tx = lerp(stackTx, endTx, p),
        ty = lerp(stackTy, endTy, p),
        rotation = lerp(motion.stackRotation, 0f, p),
        scale = lerp(stackedScale, 1f, p),
    )
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}
