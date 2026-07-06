package com.wordflip.feature.study

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.ui.component.FlipCard
import com.wordflip.core.ui.component.FlowingSyllableWord
import com.wordflip.core.ui.component.WordFlipBottomSheet

/**
 * 长按详情抽屉（REQ-STUDY-14~19）：词义详情 + 污渍/照片快捷按钮。
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
    onChangeStain: (String, List<StainType>) -> Unit,
    onToggleStainVisibility: (String) -> Unit,
    onToggleShowCnOnImage: (String) -> Unit,
    onTakePhoto: (String) -> Unit,
    onPickGallery: (String) -> Unit,
    onEditPhoto: (String) -> Unit,
) {
    WordFlipBottomSheet(
        visible = visible && word != null,
        onDismiss = onDismiss,
    ) {
        word ?: return@WordFlipBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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

            // REQ-STUDY-18~19：污渍/照片收进小按钮 + 下拉菜单，避免占满抽屉高度
            HorizontalDivider()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DetailQuickActionRow(
                        icon = Icons.Outlined.Palette,
                        iconContentDescription = "卡片污渍",
                        title = "卡片污渍",
                        subtitle = stainStatusLabel(word),
                        trailingContent = {
                            StainCardPreview(word = word)
                        },
                        menuContent = { dismissMenu ->
                            DropdownMenuItem(
                                text = { Text("随机换一个") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Autorenew, contentDescription = null)
                                },
                                onClick = {
                                    dismissMenu()
                                    onChangeStain(word.wordKey, StainType.entries)
                                },
                            )
                            StainType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text("换一个 · ${stainTypeLabel(type)}") },
                                    onClick = {
                                        dismissMenu()
                                        onChangeStain(word.wordKey, listOf(type))
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(if (word.stain.hidden) "显示污渍" else "隐藏污渍")
                                },
                                onClick = {
                                    dismissMenu()
                                    onToggleStainVisibility(word.wordKey)
                                },
                            )
                        },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DetailQuickActionRow(
                            icon = Icons.Outlined.PhotoCamera,
                            iconContentDescription = "卡片照片",
                            title = "卡片照片",
                            subtitle = if (word.image.hasImage) {
                                "已添加 · 点击相机管理"
                            } else {
                                "点击相机添加"
                            },
                            modifier = Modifier.weight(1f),
                            menuContent = { dismissMenu ->
                                DropdownMenuItem(
                                    text = { Text("拍照") },
                                    onClick = {
                                        dismissMenu()
                                        onTakePhoto(word.wordKey)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("从相册选择") },
                                    onClick = {
                                        dismissMenu()
                                        onPickGallery(word.wordKey)
                                    },
                                )
                                if (word.image.hasImage) {
                                    DropdownMenuItem(
                                        text = { Text("编辑照片") },
                                        onClick = {
                                            dismissMenu()
                                            onEditPhoto(word.wordKey)
                                        },
                                    )
                                }
                            },
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "显示中文",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Switch(
                                checked = word.image.showCnOnImage,
                                enabled = word.image.hasImage,
                                onCheckedChange = { onToggleShowCnOnImage(word.wordKey) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 详情抽屉内单行快捷操作： tonal 图标按钮 + 标题 + 可选预览 + 下拉菜单 */
@Composable
private fun DetailQuickActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconContentDescription: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    menuContent: @Composable (dismissMenu: () -> Unit) -> Unit,
) {
    var menuExpanded by remember(iconContentDescription) { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box {
            FilledTonalIconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                menuContent { menuExpanded = false }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailingContent?.invoke()
    }
}

/** REQ-STUDY-18：卡片正面污渍 WYSIWYG 迷你预览，随换一个/隐藏即时刷新 */
@Composable
private fun StainCardPreview(
    word: WordCard,
    modifier: Modifier = Modifier,
) {
    key(word.stain.seed, word.stain.hidden, word.stain.config?.seed) {
        FlipCard(
            en = word.en,
            cn = word.cn,
            ph = word.ph,
            pos = word.pos,
            wordKey = word.wordKey,
            stainSeed = word.stain.seed,
            stainHidden = word.stain.hidden,
            stainConfig = word.stain.config,
            isFlipped = false,
            onClick = {},
            onLongClick = {},
            interactionEnabled = false,
            modifier = modifier.width(76.dp),
        )
    }
}

private fun stainStatusLabel(word: WordCard): String =
    if (word.stain.hidden) "已隐藏 · 点击调色板管理" else "点击调色板换一个"

private fun stainTypeLabel(type: StainType): String = when (type) {
    StainType.COFFEE -> "咖啡"
    StainType.INK -> "墨水"
    StainType.HIGHLIGHT -> "荧光"
    StainType.CRAYON -> "蜡笔"
    StainType.RANDOM_LINE -> "线条"
}
