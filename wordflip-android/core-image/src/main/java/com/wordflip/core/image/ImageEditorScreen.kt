package com.wordflip.core.image

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.WordFlipTopBarAction
import com.wordflip.core.ui.image.CardImagePreview

/**
 * 图片编辑器（REQ-SNAP-5、P3-A05）：上方卡片 WYSIWYG 预览，下方精简工具栏。
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

    // 拦截系统返回/侧滑：仅关闭编辑器，不冒泡到 NavHost 退出学习页
    BackHandler(onBack = onDismiss)

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
            // 预览区：深色舞台 + 真实卡片比例
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF2A2A2E)),
                contentAlignment = Alignment.Center,
            ) {
                CardImagePreview(
                    imageUri = imageUri,
                    cn = cn,
                    transform = transform,
                    filters = filters,
                    showCnOnImage = showCn,
                    onTransformChange = { transform = it },
                    modifier = Modifier.padding(20.dp),
                )
            }

            // 精简工具栏：固定高度，无需整页滚动
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 位置：旋转 + 缩放
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EditorIconButton(
                            icon = Icons.Outlined.RotateLeft,
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
                            icon = Icons.Outlined.RotateRight,
                            label = "右转",
                            onClick = {
                                transform = transform.copy(rotate = transform.rotate + 90f)
                            },
                        )
                    }

                    // 滤镜预设：横向 chips，替代 5 条滑杆
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
                            )
                        }
                    }

                    // 选项行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "显示中文",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Switch(
                                checked = showCn,
                                onCheckedChange = {
                                    showCn = it
                                    transform = transform.copy(showCn = it)
                                },
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        OutlinedButton(onClick = onReplaceImage) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("换图", modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = { onSave(transform, filters, showCn) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("保存到卡片")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
