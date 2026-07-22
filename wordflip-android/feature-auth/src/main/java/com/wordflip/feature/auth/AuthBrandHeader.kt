package com.wordflip.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.wordflip.core.ui.apple.ApplePageTitle
import com.wordflip.core.ui.apple.AppleUi

/** Auth 品牌区样式：根据流程提供对应的大标题与简短说明。 */
enum class AuthBrandStyle {
    /** 登录页欢迎文案。 */
    Login,
    /** 注册或找回密码页文案。 */
    RegisterCompact,
}

/**
 * Auth 品牌头部：小型字标用于识别产品，大标题明确当前任务。
 *
 * @param pageTitle 子页标题（如「找回密码」），仅 RegisterCompact 时显示在字标下方
 */
@Composable
fun AuthBrandHeader(
    style: AuthBrandStyle,
    modifier: Modifier = Modifier,
    pageTitle: String? = null,
) {
    val title = when {
        style == AuthBrandStyle.Login -> "欢迎回来"
        pageTitle != null -> pageTitle
        else -> "创建账号"
    }
    val subtitle = when {
        style == AuthBrandStyle.Login -> "登录 WordFlip，继续今天的学习。"
        pageTitle != null -> "验证账号后，设置新的登录密码。"
        else -> "选择常用方式，开始建立你的学习计划。"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "WORDFLIP",
            style = MaterialTheme.typography.labelLarge,
            color = AppleUi.colors.accent,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        ApplePageTitle(
            title = title,
            subtitle = subtitle,
        )
    }
}
