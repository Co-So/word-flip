package com.wordflip.feature.study

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.study.Sense
import com.wordflip.core.model.study.SourceMaterial
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.ui.apple.AppleContextActionRow
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.component.WordFlipBottomSheet

/** 学习卡详情：主考义固定展开，外部词典资料按来源独立展开。 */
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
    WordFlipBottomSheet(visible = visible && word != null, onDismiss = onDismiss) {
        word ?: return@WordFlipBottomSheet
        var expandedSourceIds by remember(word.wordKey, word.sourceMaterials) {
            mutableStateOf(emptySet<String>())
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("关闭")
                }
            }

            WordHeader(
                word = word,
                isDetailSpeaking = isDetailSpeaking,
                onSpeak = onSpeak,
            )
            SpeechRateStepper(
                speechRate = speechRate,
                onRateDown = onRateDown,
                onRateUp = onRateUp,
            )

            HorizontalDivider(color = AppleUi.colors.separator)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "本书考义",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppleUi.colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
                // 主考义始终来自当前词书，不被展开的来源资料覆盖。
                SenseSection(word.sensesForDetail())
            }

            if (word.sourceMaterials.isNotEmpty()) {
                HorizontalDivider(color = AppleUi.colors.separator)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "来源资料",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppleUi.colors.primaryText,
                        fontWeight = FontWeight.SemiBold,
                    )
                    word.sourceMaterials.forEach { source ->
                        val expanded = source.sourceId in expandedSourceIds
                        SourceMaterialCard(
                            source = source,
                            expanded = expanded,
                            onToggle = {
                                expandedSourceIds = if (expanded) {
                                    expandedSourceIds - source.sourceId
                                } else {
                                    expandedSourceIds + source.sourceId
                                }
                            },
                        )
                    }
                }
            }

            HorizontalDivider(color = AppleUi.colors.separator)
            MemoryMaterialsSection(
                word = word,
                onChangeStain = onChangeStain,
                onToggleStainVisibility = onToggleStainVisibility,
                onToggleShowCnOnImage = onToggleShowCnOnImage,
                onTakePhoto = onTakePhoto,
                onPickGallery = onPickGallery,
                onEditPhoto = onEditPhoto,
            )
        }
    }
}

/** 展示词形、音标词性与可重复触发的朗读主操作。 */
@Composable
private fun WordHeader(
    word: WordCard,
    isDetailSpeaking: Boolean,
    onSpeak: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = word.en,
                style = MaterialTheme.typography.headlineLarge,
                color = AppleUi.colors.primaryText,
                fontWeight = FontWeight.Bold,
            )
            val pronunciation = listOfNotNull(
                word.ph?.takeIf { it.isNotBlank() },
                word.pos?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Text(
                text = pronunciation.ifBlank { "暂无音标或词性" },
                style = MaterialTheme.typography.bodyMedium,
                color = AppleUi.colors.secondaryText,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = onSpeak,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        stateDescription = if (isDetailSpeaking) "播放中" else "未播放"
                    },
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = AppleUi.colors.accent,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = if (isDetailSpeaking) {
                            "再次朗读 ${word.en}"
                        } else {
                            "朗读 ${word.en}"
                        },
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Text(
                text = if (isDetailSpeaking) "播放中" else "朗读",
                style = MaterialTheme.typography.bodySmall,
                color = if (isDetailSpeaking) AppleUi.colors.accent else AppleUi.colors.secondaryText,
            )
        }
    }
}

/** 使用固定 48dp 控件呈现 0.5x–2.0x 语速步进器。 */
@Composable
private fun SpeechRateStepper(
    speechRate: Float,
    onRateDown: () -> Unit,
    onRateUp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "朗读语速",
                style = MaterialTheme.typography.titleSmall,
                color = AppleUi.colors.primaryText,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "可调范围 0.5x–2.0x",
                style = MaterialTheme.typography.bodySmall,
                color = AppleUi.colors.secondaryText,
            )
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AppleUi.colors.groupedSurface,
        ) {
            Row(
                modifier = Modifier
                    .width(160.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onRateDown,
                    enabled = speechRate > 0.5f,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Outlined.Remove, contentDescription = "降低朗读语速")
                }
                Text(
                    text = "${"%.1f".format(speechRate)}x",
                    modifier = Modifier.weight(1f),
                    color = AppleUi.colors.primaryText,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(
                    onClick = onRateUp,
                    enabled = speechRate < 2.0f,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "提高朗读语速")
                }
            }
        }
    }
}

/** 单个来源资料卡：摘要常驻，义项由用户按需展开。 */
@Composable
private fun SourceMaterialCard(
    source: SourceMaterial,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val metadata = listOfNotNull(
        source.revision.takeIf { it.isNotBlank() },
        source.licenseNote?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifBlank { "暂无版本与授权摘要" }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppleUi.colors.groupedSurface,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = source.sourceName,
                        style = MaterialTheme.typography.titleSmall,
                        color = AppleUi.colors.primaryText,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${source.senses.size} 个义项 · $metadata",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleUi.colors.secondaryText,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) {
                        "收起 ${source.sourceName} 资料"
                    } else {
                        "展开 ${source.sourceName} 资料"
                    },
                    modifier = Modifier.padding(12.dp),
                    tint = AppleUi.colors.secondaryText,
                )
            }
            if (expanded) {
                HorizontalDivider(color = AppleUi.colors.separator)
                Column(modifier = Modifier.padding(16.dp)) {
                    SenseSection(source.senses)
                }
            }
        }
    }
}

/** 将图片、污渍与中文叠字操作收纳为同一记忆素材分组。 */
@Composable
private fun MemoryMaterialsSection(
    word: WordCard,
    onChangeStain: (String, List<StainType>) -> Unit,
    onToggleStainVisibility: (String) -> Unit,
    onToggleShowCnOnImage: (String) -> Unit,
    onTakePhoto: (String) -> Unit,
    onPickGallery: (String) -> Unit,
    onEditPhoto: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "记忆素材",
            style = MaterialTheme.typography.titleMedium,
            color = AppleUi.colors.primaryText,
            fontWeight = FontWeight.SemiBold,
        )
        AppleGroupedSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            MaterialGroupLabel("图片")
            MaterialActionRow(
                label = "拍照",
                icon = Icons.Outlined.PhotoCamera,
                iconDescription = "拍照添加记忆图片",
                onClick = { onTakePhoto(word.wordKey) },
            )
            MaterialActionRow(
                label = "从相册选择",
                icon = Icons.Outlined.PhotoLibrary,
                iconDescription = "从相册选择记忆图片",
                onClick = { onPickGallery(word.wordKey) },
            )
            if (word.image.hasImage) {
                MaterialActionRow(
                    label = "编辑图片",
                    icon = Icons.Outlined.Edit,
                    iconDescription = "编辑记忆图片",
                    onClick = { onEditPhoto(word.wordKey) },
                )
            }

            HorizontalDivider(color = AppleUi.colors.separator)
            MaterialGroupLabel("污渍")
            MaterialActionRow(
                label = "更换污渍",
                icon = Icons.Outlined.AutoFixHigh,
                iconDescription = "更换卡片污渍",
                onClick = { onChangeStain(word.wordKey, StainType.entries) },
            )
            MaterialActionRow(
                label = if (word.stain.hidden) "显示污渍" else "隐藏污渍",
                icon = if (word.stain.hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                iconDescription = if (word.stain.hidden) "显示卡片污渍" else "隐藏卡片污渍",
                onClick = { onToggleStainVisibility(word.wordKey) },
            )

            if (word.image.hasImage) {
                HorizontalDivider(color = AppleUi.colors.separator)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "图片显示中文",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppleUi.colors.primaryText,
                        )
                        Text(
                            text = "在记忆图片底部叠加本书释义",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppleUi.colors.secondaryText,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = word.image.showCnOnImage,
                        onCheckedChange = { onToggleShowCnOnImage(word.wordKey) },
                    )
                }
            }
        }
    }
}

/** 记忆素材卡内的轻量分组标签。 */
@Composable
private fun MaterialGroupLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = AppleUi.colors.secondaryText,
        fontWeight = FontWeight.SemiBold,
    )
}

/** 次要素材操作统一使用低强调行样式，同时保持 48dp 触控目标。 */
@Composable
private fun MaterialActionRow(
    label: String,
    icon: ImageVector,
    iconDescription: String,
    onClick: () -> Unit,
) {
    AppleContextActionRow(
        label = label,
        onClick = onClick,
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = AppleUi.colors.secondaryText,
            )
        },
    )
}

/** 依服务端顺序展示义项和例句，不在客户端重算考义。 */
@Composable
private fun SenseSection(senses: List<Sense>) {
    if (senses.isEmpty()) {
        Text("暂无可用义项", color = AppleUi.colors.secondaryText)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        senses.sortedBy { it.sortOrder }.forEach { sense ->
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sense.pos?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                    Text(sense.displayMeaning())
                }
                sense.examples.sortedBy { it.sortOrder }.forEach { example ->
                    Text(
                        "• ${example.en}${example.cn?.let { " — $it" }.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleUi.colors.secondaryText,
                    )
                }
            }
        }
    }
}
