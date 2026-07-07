package com.wordflip.feature.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wordflip.core.model.study.WordSummary
import com.wordflip.core.ui.component.WordFlipBottomSheet

/**
 * 词书导入预览确认 BottomSheet（REQ-BOOK-5~9）。
 */
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
    ) {
        Text(
            text = "导入预览",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        OutlinedTextField(
            value = state.nameInput,
            onValueChange = onNameChange,
            label = { Text("词书名称") },
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )
        Text(
            text = "共 ${state.totalCount} 词（预览前 ${state.previewWords.size} 个）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.previewWords.forEach { word ->
                PreviewWordRow(word)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !state.isConfirming,
                modifier = Modifier.weight(1f),
            ) {
                Text("取消")
            }
            Button(
                onClick = onConfirm,
                enabled = !state.isConfirming,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isConfirming) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text("导入")
            }
        }
    }
}

@Composable
private fun PreviewWordRow(word: WordSummary) {
    Text(
        text = "${word.en} — ${word.cn}",
        style = MaterialTheme.typography.bodyMedium,
    )
}
