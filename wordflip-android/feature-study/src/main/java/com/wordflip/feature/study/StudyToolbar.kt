package com.wordflip.feature.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 学习页工具栏（REQ-STUDY-2）：打乱、全翻正面/背面。
 */
@Composable
fun StudyToolbar(
    allFlippedToBack: Boolean,
    isShuffling: Boolean,
    onShuffle: () -> Unit,
    onFlipAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onShuffle,
            enabled = !isShuffling,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.Shuffle, contentDescription = null)
            Text(
                text = if (isShuffling) "打乱中…" else "打乱",
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        OutlinedButton(
            onClick = onFlipAll,
            enabled = !isShuffling,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = if (allFlippedToBack) Icons.Outlined.FlipToFront else Icons.Outlined.FlipToBack,
                contentDescription = null,
            )
            Text(
                text = if (allFlippedToBack) "全翻正面" else "全翻背面",
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
