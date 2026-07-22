package com.wordflip.feature.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wordflip.core.model.study.WordSummary
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.ApplePrimaryAction
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.component.WordFlipBottomSheet

/** 词书导入预览：分层展示解析摘要、名称编辑、词条样本与最终确认。 */
@Composable
fun BookImportPreviewSheet(
    state: ImportSheetState,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WordFlipBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        dismissible = !state.isConfirming,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = AppleUi.colors.accent.copy(alpha = 0.12f),
                    tonalElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(23.dp),
                            tint = AppleUi.colors.accent,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "导入预览",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppleUi.colors.primaryText,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "文件解析完成，请确认词书信息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppleUi.colors.secondaryText,
                    )
                }
            }

            AppleGroupedSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "解析结果",
                            style = MaterialTheme.typography.labelLarge,
                            color = AppleUi.colors.primaryText,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "预览前 ${state.previewWords.size} 个词条",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppleUi.colors.secondaryText,
                        )
                    }
                    Text(
                        text = "${state.totalCount} 词",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppleUi.colors.accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            OutlinedTextField(
                value = state.nameInput,
                onValueChange = onNameChange,
                enabled = !state.isConfirming,
                label = { Text("词书名称") },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "词条预览",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppleUi.colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
                AppleGroupedSurface(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 224.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (state.previewWords.isEmpty()) {
                            Text(
                                text = "文件中没有可预览的词条",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppleUi.colors.secondaryText,
                            )
                        } else {
                            state.previewWords.forEachIndexed { index, word ->
                                PreviewWordRow(index = index + 1, word = word)
                                if (index != state.previewWords.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 52.dp),
                                        color = AppleUi.colors.separator,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ApplePrimaryAction(
                    text = if (state.isConfirming) "正在导入…" else "确认导入 ${state.totalCount} 词",
                    onClick = onConfirm,
                    enabled = !state.isConfirming,
                    leadingContent = if (state.isConfirming) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        null
                    },
                )
                TextButton(
                    onClick = onDismiss,
                    enabled = !state.isConfirming,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("取消")
                }
            }
        }
    }
}

/** 预览行只呈现服务端解析结果，不在客户端更改或补全词义。 */
@Composable
private fun PreviewWordRow(
    index: Int,
    word: WordSummary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = AppleUi.colors.secondaryText,
            modifier = Modifier.size(28.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = word.en,
                style = MaterialTheme.typography.bodyLarge,
                color = AppleUi.colors.primaryText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            word.cn?.takeIf { it.isNotBlank() }?.let { meaning ->
                Text(
                    text = meaning,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleUi.colors.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
