package com.wordflip.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 卡片内字段间距 */
internal val AuthFieldSpacing = 12.dp

/** 卡片间间距 */
internal val AuthCardSpacing = 16.dp

/**
 * Auth 表单卡片：surface 白底 + radius.md 16dp，可选分区标题与底部 CTA 槽位。
 *
 * @param sectionTitle 卡片内轻量分区标题（如注册页「设置密码」）
 * @param footer 主 CTA 等底部操作，与字段区以 outline 分隔线隔开
 */
@Composable
fun AuthFormCard(
    modifier: Modifier = Modifier,
    sectionTitle: String? = null,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AuthFieldSpacing),
        ) {
            if (sectionTitle != null) {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
            if (footer != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                footer()
            }
        }
    }
}
