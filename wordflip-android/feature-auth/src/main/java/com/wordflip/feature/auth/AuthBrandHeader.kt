package com.wordflip.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Auth 品牌区样式：登录全量 / 注册与子页紧凑 */
enum class AuthBrandStyle {
    /** 登录页：WordFlip 字标 + slogan */
    Login,
    /** 注册/子页：紧凑 WordFlip 字标，可配 pageTitle */
    RegisterCompact,
}

/**
 * Auth 品牌头部（A 类页点缀色控制在 primary 字标）。
 *
 * @param pageTitle 子页标题（如「找回密码」），仅 RegisterCompact 时显示在字标下方
 */
@Composable
fun AuthBrandHeader(
    style: AuthBrandStyle,
    modifier: Modifier = Modifier,
    pageTitle: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (style) {
            AuthBrandStyle.Login -> {
                Text(
                    text = "WordFlip",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "翻转卡片，自然记词",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            AuthBrandStyle.RegisterCompact -> {
                Text(
                    text = "WordFlip",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (pageTitle != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
