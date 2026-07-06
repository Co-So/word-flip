package com.wordflip.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 居中单词展示：元音字母橙色高亮 + 朗读时逐字母流动动画（对齐 v5 `renderSyllableWord`）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowingSyllableWord(
    word: String,
    isSpeaking: Boolean,
    speechRate: Float,
    modifier: Modifier = Modifier,
    /** 为 false 时仅元音着色，不播放流动动画 */
    animateFlow: Boolean = true,
) {
    val letterIndices = remember(word) { word.indices.filter { word[it].isLetter() } }
    var flowingIndex by remember(word) { mutableIntStateOf(-1) }
    val letterCount = remember(word) { letterIndices.size.coerceAtLeast(1) }

    LaunchedEffect(isSpeaking, word, speechRate, animateFlow) {
        if (!isSpeaking || !animateFlow) {
            flowingIndex = -1
            return@LaunchedEffect
        }
        val durationMs = ((letterCount * 80) / speechRate).toLong().coerceAtLeast(300L)
        val start = System.currentTimeMillis()
        while (isActive && System.currentTimeMillis() - start < durationMs) {
            val elapsed = System.currentTimeMillis() - start
            val progress = elapsed.toFloat() / durationMs
            val letterPos = (progress * letterIndices.size).toInt()
                .coerceIn(0, (letterIndices.size - 1).coerceAtLeast(0))
            flowingIndex = letterIndices.getOrElse(letterPos) { -1 }
            delay(16)
        }
        flowingIndex = -1
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        word.forEachIndexed { index, char ->
            when {
                char.isLetter() -> {
                    val isVowel = char.lowercaseChar() in VOWELS
                    val isFlowing = index == flowingIndex
                    val scale by animateFloatAsState(
                        targetValue = if (isFlowing) 1.15f else 1f,
                        animationSpec = spring(stiffness = 400f),
                        label = "letterScale",
                    )
                    val yOffset by animateFloatAsState(
                        targetValue = if (isFlowing) -3f else 0f,
                        animationSpec = spring(stiffness = 400f),
                        label = "letterOffset",
                    )
                    Text(
                        text = char.toString(),
                        modifier = Modifier
                            .offset(y = yOffset.dp)
                            .scale(scale)
                            .padding(horizontal = 0.5.dp),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default,
                        color = when {
                            isFlowing -> MaterialTheme.colorScheme.primary
                            isVowel -> Color(0xFFE85D04)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    Text(
                        text = char.toString(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')
