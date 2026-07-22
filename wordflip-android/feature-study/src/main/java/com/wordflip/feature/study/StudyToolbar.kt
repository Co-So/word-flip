package com.wordflip.feature.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wordflip.core.ui.apple.AppleGlassSurface
import com.wordflip.core.ui.apple.applePress

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
    val shuffleInteractionSource = remember { MutableInteractionSource() }
    val flipInteractionSource = remember { MutableInteractionSource() }

    AppleGlassSurface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        cornerRadius = 18.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = onShuffle,
                enabled = !isShuffling,
                interactionSource = shuffleInteractionSource,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .applePress(
                        interactionSource = shuffleInteractionSource,
                        enabled = !isShuffling,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (isShuffling) "打乱中…" else "打乱",
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextButton(
                onClick = onFlipAll,
                enabled = !isShuffling,
                interactionSource = flipInteractionSource,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .applePress(
                        interactionSource = flipInteractionSource,
                        enabled = !isShuffling,
                    ),
            ) {
                Icon(
                    imageVector = if (allFlippedToBack) {
                        Icons.Outlined.FlipToFront
                    } else {
                        Icons.Outlined.FlipToBack
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (allFlippedToBack) "全翻正面" else "全翻背面",
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
