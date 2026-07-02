package com.wordflip.feature.study

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.ui.component.WordFlipBottomSheet

/**
 * 长按详情抽屉（REQ-STUDY-14~17）：居中单词 + 元音着色 + 流动朗读动画，对齐 v5 sheet 布局。
 */
@Composable
fun StudyDetailSheet(
    word: WordCard?,
    visible: Boolean,
    speechRate: Float,
    isDetailSpeaking: Boolean,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
    onRateDown: () -> Unit,
    onRateUp: () -> Unit,
    onPlaceholderAction: (String) -> Unit,
) {
    WordFlipBottomSheet(
        visible = visible && word != null,
        onDismiss = onDismiss,
    ) {
        word ?: return@WordFlipBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // v5 .sheet-word：居中、加粗
            FlowingSyllableWord(
                word = word.en,
                isSpeaking = isDetailSpeaking,
                speechRate = speechRate,
                animateFlow = true,
            )
            word.ph?.let { ph ->
                Text(
                    text = ph,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = word.cn,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            word.pos?.let { pos ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = pos,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // v5 .speak-controls：语速 + 圆形朗读按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onRateDown) { Text("−") }
                Text(
                    text = "${"%.1f".format(speechRate)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = onRateUp) { Text("+") }
                val infiniteTransition = rememberInfiniteTransition(label = "speakPulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pulseScale",
                )
                FilledIconButton(
                    onClick = onSpeak,
                    modifier = Modifier.scale(if (isDetailSpeaking) pulseScale else 1f),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VolumeUp,
                        contentDescription = "朗读",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "词义",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = word.detail?.meaning ?: word.cn,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "例句",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val examples = word.detail?.examples.orEmpty()
                if (examples.isEmpty()) {
                    Text(text = "暂无例句", style = MaterialTheme.typography.bodyMedium)
                } else {
                    examples.forEach { example ->
                        Text(text = "• $example", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                word.detail?.etymology?.let { etymology ->
                    Text(
                        text = "词根词缀",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = etymology, style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onPlaceholderAction("换一个污渍功能即将上线") }) {
                    Text("换一个污渍")
                }
                OutlinedButton(onClick = { onPlaceholderAction("卡拍功能即将上线") }) {
                    Text("卡片照片")
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("关闭")
            }
        }
    }
}
