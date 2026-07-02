package com.wordflip.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordflip.core.ui.theme.WordFlipColors

/**
 * 判题反馈条（REQ-QUIZ-5~7）：1.4s 展示后自动下一题。
 */
@Composable
fun QuizFeedbackBanner(
    message: String,
    isCorrect: Boolean,
    expectedEn: String? = null,
    modifier: Modifier = Modifier,
) {
    val color = if (isCorrect) {
        WordFlipColors.extra.success
    } else {
        MaterialTheme.colorScheme.error
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = buildString {
                append(message)
                if (!isCorrect && expectedEn != null) {
                    append(" · 答案：$expectedEn")
                }
            },
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}
