package com.wordflip.core.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.model.media.StainConfig
import com.wordflip.core.ui.image.WordImageBack
import com.wordflip.core.ui.theme.SageDarkStudyCard
import com.wordflip.core.ui.theme.SageStudyCard
import com.wordflip.core.ui.theme.SageStudyCardBack
import com.wordflip.core.ui.theme.SageStudyCardBackPh
import com.wordflip.core.ui.theme.SageStudyCardBackText
import com.wordflip.core.ui.theme.SageStudyCardInk
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.min

/**
 * 双面翻转学习卡片（REQ-STUDY-3~8）：3:4.2 比例、可中断 spring 3D 翻转。
 * REQ-STUDY-14：按下即时缩小反馈，长按提交时保留一次触觉反馈。
 */
@Composable
fun FlipCard(
    en: String,
    cn: String?,
    ph: String?,
    pos: String?,
    stainSeed: Long,
    stainHidden: Boolean,
    stainConfig: StainConfig? = null,
    wordKey: String = en.lowercase(),
    isFlipped: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionEnabled: Boolean = true,
    hasImage: Boolean = false,
    imageUrl: String? = null,
    imageTransform: ImageTransform? = null,
    imageFilters: ImageFilters? = null,
    showCnOnImage: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    // 按下即响应，并允许松手或翻转途中从当前值平滑反向
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "cardPressScale",
    )

    // 翻转使用无回弹 spring，快速连点时可以从当前角度继续或反向
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "flipRotation",
    )

    val isDark = isSystemInDarkTheme()
    val frontColor = if (isDark) SageDarkStudyCard else SageStudyCard
    val backColor = SageStudyCardBack
    val accessibilityDescription = listOfNotNull(
        en,
        if (isFlipped) "背面" else "正面",
        cn?.takeIf { isFlipped && it.isNotBlank() },
    ).joinToString("，")

    Box(
        modifier = modifier
            .aspectRatio(3f / 4.2f)
            // 卡面文字由根节点统一描述，避免装饰性子节点重复播报。
            .clearAndSetSemantics {
                contentDescription = accessibilityDescription
                role = Role.Button
                if (interactionEnabled) {
                    onClick(label = "翻面") {
                        onClick()
                        true
                    }
                    onLongClick(label = "查看详情") {
                        // 辅助操作不重复触发触摸路径的长按触觉。
                        onLongClick()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .studyCardPointerInput(
                enabled = interactionEnabled,
                interactionSource = interactionSource,
                longPressTimeoutMillis = 500L,
                pressFeedbackDelayMillis = 0L,
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
                    scaleX = pressScale
                    scaleY = pressScale
                    rotationY = rotation
                    cameraDistance = 8f * density
                },
        ) {
            if (rotation <= 90f) {
                CardFace(
                    modifier = Modifier.fillMaxSize(),
                    backgroundColor = frontColor,
                    isPressed = isPressed,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        StainOverlay(
                            wordKey = wordKey,
                            seed = stainSeed,
                            config = stainConfig,
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
                    isPressed = isPressed,
                ) {
                    CardBackContent(
                        cn = cn,
                        pos = pos,
                        ph = ph,
                        hasImage = hasImage,
                        imageUrl = imageUrl,
                        imageTransform = imageTransform,
                        imageFilters = imageFilters,
                        showCnOnImage = showCnOnImage,
                    )
                }
            }
        }
    }
}

/** 背面：无图时 primary 释义（cn + pos + ph）；有图时黑底 + 底部 overlay */
@Composable
private fun CardBackContent(
    cn: String?,
    pos: String?,
    ph: String?,
    hasImage: Boolean,
    imageUrl: String?,
    imageTransform: ImageTransform?,
    imageFilters: ImageFilters?,
    showCnOnImage: Boolean,
) {
    val displayCn = cn ?: ""
    if (hasImage && !imageUrl.isNullOrBlank()) {
        WordImageBack(
            imageUri = imageUrl,
            cn = displayCn,
            transform = imageTransform,
            filters = imageFilters,
            showCnOnImage = showCnOnImage,
        )
    } else if (hasImage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = displayCn,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SageStudyCardBackText,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (!pos.isNullOrBlank()) {
                Text(
                    text = pos,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 12.sp,
                    color = SageStudyCardBackPh,
                    textAlign = TextAlign.Center,
                )
            }
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
 * REQ-STUDY-14：按下即 emit PressInteraction，抬起后才提交短按；长按与短按保持互斥。
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
    isPressed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 4.dp,
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
