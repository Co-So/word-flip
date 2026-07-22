package com.wordflip.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.AppleUi

/** 卡片内字段间距 */
internal val AuthFieldSpacing = 12.dp

/** 卡片间间距 */
internal val AuthCardSpacing = 16.dp

/**
 * Auth 表单区：输入集中在分组表面，主操作与输入表面分离以强化层级。
 *
 * @param sectionTitle 卡片内轻量分区标题（如注册页「设置密码」）
 * @param footer 主 CTA 等底部操作，显示在分组表面下方
 */
@Composable
fun AuthFormCard(
    modifier: Modifier = Modifier,
    sectionTitle: String? = null,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AuthCardSpacing),
    ) {
        AppleGroupedSurface(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(AuthFieldSpacing),
            ) {
                if (sectionTitle != null) {
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = AppleUi.colors.secondaryText,
                    )
                }
                content()
            }
        }
        footer?.invoke()
    }
}
