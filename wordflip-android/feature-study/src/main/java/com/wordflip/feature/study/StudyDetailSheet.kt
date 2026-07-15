package com.wordflip.feature.study

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import com.wordflip.core.model.book.DictionaryItem
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.study.Sense
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.ui.component.FlipCard
import com.wordflip.core.ui.component.FlowingSyllableWord
import com.wordflip.core.ui.component.WordFlipBottomSheet

/**
 * 长按详情抽屉（REQ-STUDY-14~19）：词义详情 + 词典切换 + 污渍/照片快捷按钮。
 * <p>
 * 词典切换：展示当前 activeDictId 的释义，可临时切到其他词典查看，不影响全局设置。
 * 预留 AI 释义扩展位（Psychology 图标占位）。
 */
@Composable
fun StudyDetailSheet(
    word: WordCard?,
    visible: Boolean,
    speechRate: Float,
    isDetailSpeaking: Boolean,
    dictionaries: List<DictionaryItem>,
    dictLookup: DictLookupState,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
    onRateDown: () -> Unit,
    onRateUp: () -> Unit,
    onSwitchDict: (String) -> Unit,
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

            // 词典切换 Chip 行（REQ-LEX-9 详情抽屉临时切换）
            DictSwitcherRow(
                word = word,
                dictionaries = dictionaries,
                dictLookup = dictLookup,
                onSwitchDict = onSwitchDict,
            )

            // 主释义：优先展示词典切换后的释义，否则用原始卡片释义
            val displayMeaning = when (dictLookup) {
                is DictLookupState.Success -> dictLookup.cn?.takeIf { it.isNotBlank() }
                    ?: dictLookup.enGloss?.takeIf { it.isNotBlank() }
                    ?: word.displayMeaning()
                else -> word.displayMeaning()
            }
            val displayPos = when (dictLookup) {
                is DictLookupState.Success -> dictLookup.pos
                else -> word.pos
            }
            val displayPh = when (dictLookup) {
                is DictLookupState.Success -> dictLookup.ph
                else -> word.ph
            }

            Text(
                text = displayMeaning,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            displayPos?.let { pos ->
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

            // 义项展示：优先用词典切换后的 senses，否则用原始卡片 senses
            when (dictLookup) {
                is DictLookupState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                is DictLookupState.Success -> {
                    SenseDetailSection(
                        senses = dictLookup.senses,
                        en = word.en,
                        fallbackMeaning = word.displayMeaning(),
                    )
                }
                is DictLookupState.Error -> {
                    Text(
                        text = dictLookup.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    // 出错时回退展示原始释义
                    SenseDetailSection(
                        senses = word.sensesForDetail(),
                        en = word.en,
                        fallbackMeaning = word.displayMeaning(),
                    )
                }
                DictLookupState.Idle -> {
                    SenseDetailSection(
                        senses = word.sensesForDetail(),
                        en = word.en,
                        fallbackMeaning = word.displayMeaning(),
                    )
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

/**
 * 词典切换 Chip 行：展示可用词典，当前选中高亮；预留 AI 释义占位。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DictSwitcherRow(
    word: WordCard,
    dictionaries: List<DictionaryItem>,
    dictLookup: DictLookupState,
    onSwitchDict: (String) -> Unit,
) {
    if (dictionaries.isEmpty()) return

    val currentDictId = when (dictLookup) {
        is DictLookupState.Success -> dictLookup.requestedDictId
        else -> "wordflip_curated" // 默认展示精校，用户未切换时
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Book,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "释义来源",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                dictionaries.forEach { dict ->
                    FilterChip(
                        selected = currentDictId == dict.id,
                        onClick = { onSwitchDict(dict.id) },
                        label = { Text(dict.name, style = MaterialTheme.typography.labelSmall) },
                    )
                }
                // AI 释义占位（二期扩展）
                FilterChip(
                    selected = false,
                    onClick = { /* 二期：AI 释义 */ },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Text("AI", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    enabled = false,
                )
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

/**
 * 义项列表展示（支持词典切换后的 senses）。
 * @param senses 要展示的义项列表
 * @param en 英文词头（用于无释义时兜底）
 * @param fallbackMeaning 无 senses 时的回退释义
 */
@Composable
private fun SenseDetailSection(
    senses: List<Sense>,
    en: String,
    fallbackMeaning: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (senses.size > 1) "义项" else "词义",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (senses.isEmpty()) {
            Text(
                text = fallbackMeaning.ifBlank { "暂无释义" },
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            senses.forEachIndexed { index, sense ->
                SenseBlock(
                    index = index + 1,
                    sense = sense,
                    showIndex = senses.size > 1,
                )
            }
        }
    }
}

@Composable
private fun SenseBlock(
    index: Int,
    sense: Sense,
    showIndex: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIndex) {
                Text(
                    text = "$index.",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            sense.pos?.takeIf { it.isNotBlank() }?.let { pos ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = pos,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (sense.primary) {
                Text(
                    text = "主",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = sense.displayMeaning().ifBlank { "暂无释义" },
            style = MaterialTheme.typography.bodyLarge,
        )
        val examples = sense.examples.sortedBy { it.sortOrder }
        if (examples.isEmpty()) {
            Text(
                text = "暂无例句",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            examples.forEach { example ->
                val line = if (!example.cn.isNullOrBlank()) {
                    "${example.en} — ${example.cn}"
                } else {
                    example.en
                }
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
