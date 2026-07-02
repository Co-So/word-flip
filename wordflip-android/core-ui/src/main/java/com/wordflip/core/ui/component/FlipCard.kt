package com.wordflip.core.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordflip.core.ui.theme.SageDarkStudyCard
import com.wordflip.core.ui.theme.SageStudyCard
import com.wordflip.core.ui.theme.SageStudyCardBackText
import com.wordflip.core.ui.theme.SageStudyCardInk

/**
 * 双面翻转学习卡片（REQ-STUDY-3~8）：3:4.2 比例、spring 3D 翻转、点击按压反馈。
 */
@OptIn(ExperimentalFoundationApi::class)
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 按压时略缩小并下沉，对齐 v5 卡片交互手感
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "pressScale",
    )

    // REQ-STUDY-8：spring 翻转 ~400ms
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "flipRotation",
    )

    val isDark = isSystemInDarkTheme()
    val cardColor = if (isDark) SageDarkStudyCard else SageStudyCard

    // 点击层不参与 3D 旋转，避免翻到背面后触摸区域错位导致「只能点一面」
    Box(
        modifier = modifier
            .aspectRatio(3f / 4.2f)
            .semantics {
                contentDescription = if (isFlipped) "$en，背面，$cn" else "$en，正面"
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
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
                backgroundColor = cardColor,
                isPressed = isPressed,
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
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SageStudyCardInk,
                            textAlign = TextAlign.Center,
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
                backgroundColor = cardColor,
                isPressed = isPressed,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = cn,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SageStudyCardBackText,
                        textAlign = TextAlign.Center,
                    )
                    if (!ph.isNullOrBlank()) {
                        Text(
                            text = ph,
                            modifier = Modifier.padding(top = 6.dp),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CardFace(
    backgroundColor: androidx.compose.ui.graphics.Color,
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
