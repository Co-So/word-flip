package com.wordflip.core.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordflip.core.ui.theme.SageDarkStudyCard
import com.wordflip.core.ui.theme.SageStudyCard
import com.wordflip.core.ui.theme.SageStudyCardBack
import com.wordflip.core.ui.theme.SageStudyCardBackPh
import com.wordflip.core.ui.theme.SageStudyCardBackText
import com.wordflip.core.ui.theme.SageStudyCardInk
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.min

private val FlipEasing = CubicBezierEasing(0.34f, 1.05f, 0.64f, 1f)

/**
 * 双面翻转学习卡片（REQ-STUDY-3~8）：3:4.2 比例、v5 cubic-bezier 3D 翻转。
 * REQ-STUDY-14：短按翻转无按压反馈；长按等待期有缩小与阴影反馈。
 */
@Composable
fun FlipCard(
    en: String,
    cn: String,
    ph: String?,
    pos: String?,
    stainSeed: Long,
    stainHidden: Boolean,
    isFlipped: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionEnabled: Boolean = true,
    hasImage: Boolean = false,
    showCnOnImage: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isLongPressHolding by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    // 仅长按等待/触发时缩小，短按翻转无按压感
    val longPressScale by animateFloatAsState(
        targetValue = if (isLongPressHolding) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = if (isLongPressHolding) Spring.StiffnessMedium else Spring.StiffnessHigh,
        ),
        label = "longPressScale",
    )

    // REQ-STUDY-8：v5 翻转曲线 0.5s cubic-bezier(0.34,1.05,0.64,1)
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FlipEasing),
        label = "flipRotation",
    )

    val isDark = isSystemInDarkTheme()
    val frontColor = if (isDark) SageDarkStudyCard else SageStudyCard
    val backColor = SageStudyCardBack

    Box(
        modifier = modifier
            .aspectRatio(3f / 4.2f)
            .semantics {
                contentDescription = if (isFlipped) "$en，背面，$cn" else "$en，正面"
            }
            .studyCardPointerInput(
                enabled = interactionEnabled,
                interactionSource = interactionSource,
                longPressTimeoutMillis = 500L,
                pressFeedbackDelayMillis = 120L,
                longPressSlop = viewConfiguration.touchSlop,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = longPressScale
                    scaleY = longPressScale
                    rotationY = rotation
                    cameraDistance = 8f * density
                },
        ) {
            if (rotation <= 90f) {
                CardFace(
                    modifier = Modifier.fillMaxSize(),
                    backgroundColor = frontColor,
                    isLongPressHolding = isLongPressHolding,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        StainOverlay(
                            seed = stainSeed,
                            hidden = stainHidden,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = en,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SageStudyCardInk,
                                textAlign = TextAlign.Center,
                                lineHeight = 28.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!pos.isNullOrBlank()) {
                                Text(
                                    text = pos,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                CardFace(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f },
                    backgroundColor = backColor,
                    isLongPressHolding = isLongPressHolding,
                ) {
                    CardBackContent(
                        cn = cn,
                        ph = ph,
                        hasImage = hasImage,
                        showCnOnImage = showCnOnImage,
                    )
                }
            }
        }
    }
}

/** 背面：无图时居中中文+音标；有图时黑底 + 底部 overlay（对齐 v5 .fc-back） */
@Composable
private fun CardBackContent(
    cn: String,
    ph: String?,
    hasImage: Boolean,
    showCnOnImage: Boolean,
) {
    if (hasImage) {
        Box(modifier = Modifier.fillMaxSize()) {
            // P3 接入 Coil 后替换为真实图片；Mock 阶段黑底占位
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
            if (showCnOnImage) {
                Text(
                    text = cn,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SageStudyCardBackText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = cn,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SageStudyCardBackText,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (!ph.isNullOrBlank()) {
                Text(
                    text = ph,
                    modifier = Modifier.padding(top = 5.dp),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SageStudyCardBackPh,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * REQ-STUDY-14：短按不 emit PressInteraction；长按等待 ≥120ms 后才有按压反馈。
 * 使用 deadline 轮询，避免 withTimeout 嵌套导致长按超时无法触发。
 */
private fun Modifier.studyCardPointerInput(
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    longPressTimeoutMillis: Long,
    pressFeedbackDelayMillis: Long,
    longPressSlop: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = pointerInput(
    enabled,
    onClick,
    onLongClick,
    longPressTimeoutMillis,
    pressFeedbackDelayMillis,
) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downPos = down.position
        var movedBeyondSlop = false
        var press: PressInteraction.Press? = null
        var fingerDown = true
        val startMs = System.currentTimeMillis()
        val longPressDeadline = startMs + longPressTimeoutMillis
        val feedbackDeadline = startMs + pressFeedbackDelayMillis

        try {
            while (fingerDown && System.currentTimeMillis() < longPressDeadline) {
                val now = System.currentTimeMillis()
                if (press == null && !movedBeyondSlop && now >= feedbackDeadline) {
                    press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press!!)
                }
                val remaining = longPressDeadline - now
                if (remaining <= 0L) break

                val pollMs = min(remaining, 32L).coerceAtLeast(1L)
                val event = withTimeoutOrNull(pollMs) {
                    awaitPointerEvent()
                } ?: continue

                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    fingerDown = false
                    if (!movedBeyondSlop) onClick()
                    break
                }
                if (change.position != downPos) {
                    val dx = change.position.x - downPos.x
                    val dy = change.position.y - downPos.y
                    if (hypot(dx, dy) > longPressSlop) {
                        movedBeyondSlop = true
                        press?.let { interactionSource.tryEmit(PressInteraction.Release(it)) }
                        press = null
                        waitForUpOrCancellation()
                        fingerDown = false
                        break
                    }
                }
            }

            // 按住至 deadline 且未超 slop → 长按打开详情（REQ-STUDY-14）
            if (fingerDown && !movedBeyondSlop) {
                if (press == null) {
                    press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press!!)
                }
                onLongClick()
                waitForUpOrCancellation()
            }
        } finally {
            press?.let { interactionSource.tryEmit(PressInteraction.Release(it)) }
        }
    }
}

@Composable
private fun CardFace(
    backgroundColor: Color,
    isLongPressHolding: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val elevation by animateDpAsState(
        targetValue = if (isLongPressHolding) 1.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "cardElevation",
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        shadowElevation = elevation,
        content = content,
    )
}
