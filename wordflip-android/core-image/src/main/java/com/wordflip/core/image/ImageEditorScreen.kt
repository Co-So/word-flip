package com.wordflip.core.image

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.ui.apple.ApplePrimaryAction
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.WordFlipTopBarAction
import com.wordflip.core.ui.image.CardImagePreview

/**
 * 图片编辑器（REQ-SNAP-5、P3-A05）：沉浸预览、底部工具与唯一保存主操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    wordEn: String,
    cn: String?,
    imageUri: String,
    initialTransform: ImageTransform,
    initialFilters: ImageFilters,
    initialShowCn: Boolean,
    onDismiss: () -> Unit,
    onSave: (ImageTransform, ImageFilters, Boolean) -> Unit,
    onReplaceImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var transform by remember(imageUri) {
        mutableStateOf(initialTransform.copy(showCn = initialShowCn))
    }
    var filters by remember(imageUri) { mutableStateOf(initialFilters) }
    var showCn by remember(imageUri) { mutableStateOf(initialShowCn) }
    var preset by remember(imageUri) { mutableStateOf(initialFilters.matchPreset()) }

    // 拦截系统返回/侧滑：仅关闭编辑器，不冒泡到 NavHost 退出学习页。
    BackHandler(onBack = onDismiss)

    val colors = AppleUi.colors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.canvas,
        topBar = {
            WordFlipTopBar(
                title = wordEn,
                onNavigateBack = onDismiss,
                actions = {
                    WordFlipTopBarAction(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "重置",
                        onClick = {
                            transform = ImageTransform(showCn = showCn)
                            filters = ImageFilters()
                            preset = ImageFilterPreset.NORMAL
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 深色语义舞台让裁剪边界在明暗主题下都保持清晰，预览占据剩余空间。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.scrim),
                contentAlignment = Alignment.Center,
            ) {
                CardImagePreview(
                    imageUri = imageUri,
                    cn = cn,
                    transform = transform,
                    filters = filters,
                    showCnOnImage = showCn,
                    onTransformChange = { transform = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    hint = "拖动图片调整取景位置",
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = colors.elevatedSurface,
                shadowElevation = 12.dp,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EditorToolSectionLabel(text = "构图")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EditorIconButton(
                            icon = Icons.AutoMirrored.Outlined.RotateLeft,
                            label = "左转",
                            onClick = {
                                transform = transform.copy(rotate = transform.rotate - 90f)
                            },
                        )
                        EditorIconButton(
                            icon = Icons.Outlined.Remove,
                            label = "缩小",
                            onClick = {
                                transform = transform.copy(
                                    scale = (transform.scale - 0.1f).coerceIn(0.2f, 3f),
                                )
                            },
                        )
                        Text(
                            text = "${(transform.scale * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primaryText,
                            fontWeight = FontWeight.Bold,
                        )
                        EditorIconButton(
                            icon = Icons.Outlined.Add,
                            label = "放大",
                            onClick = {
                                transform = transform.copy(
                                    scale = (transform.scale + 0.1f).coerceIn(0.2f, 3f),
                                )
                            },
                        )
                        EditorIconButton(
                            icon = Icons.AutoMirrored.Outlined.RotateRight,
                            label = "右转",
                            onClick = {
                                transform = transform.copy(rotate = transform.rotate + 90f)
                            },
                        )
                    }

                    HorizontalDivider(color = colors.separator)
                    EditorToolSectionLabel(text = "滤镜")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ImageFilterPreset.entries.forEach { item ->
                            FilterChip(
                                selected = preset == item,
                                onClick = {
                                    preset = item
                                    filters = item.filters
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.groupedSurface,
                                    labelColor = colors.primaryText,
                                    selectedContainerColor = colors.accent.copy(alpha = 0.14f),
                                    selectedLabelColor = colors.accent,
                                ),
                            )
                        }
                    }

                    HorizontalDivider(color = colors.separator)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "显示中文",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.primaryText,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "在图片底部保留释义提示",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText,
                            )
                        }
                        Switch(
                            checked = showCn,
                            onCheckedChange = {
                                showCn = it
                                transform = transform.copy(showCn = it)
                            },
                        )
                        OutlinedButton(
                            onClick = onReplaceImage,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("更换", modifier = Modifier.padding(start = 6.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                        ) {
                            Text("取消")
                        }
                        ApplePrimaryAction(
                            text = "保存到卡片",
                            onClick = { onSave(transform, filters, showCn) },
                            modifier = Modifier.weight(1.65f),
                        )
                    }
                }
            }
        }
    }
}

/** 工具分区标题使用次级文字，避免与保存主操作争夺层级。 */
@Composable
private fun EditorToolSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = AppleUi.colors.secondaryText,
        fontWeight = FontWeight.SemiBold,
    )
}

/** 图标按钮保持 48dp 触控面积，并显式显示工具名称。 */
@Composable
private fun EditorIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = AppleUi.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.primaryText,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondaryText,
        )
    }
}
