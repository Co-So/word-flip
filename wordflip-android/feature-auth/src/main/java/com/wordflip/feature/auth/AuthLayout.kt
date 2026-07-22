package com.wordflip.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wordflip.core.ui.apple.AppleUi

/** Auth 页水平 gutter，对齐 MASTER §6 页面 16dp */
internal val AuthPageGutter = 16.dp

/** Auth 表单最大宽度，平板居中 */
internal val AuthFormMaxWidth = 400.dp

/**
 * Auth 页滚动容器：Apple 分组底色 + ime/navigationBar 安全区 + 16dp gutter。
 *
 * @param centerContent 登录页 true：内容不足一屏时垂直居中；注册页 false：顶对齐滚动。
 */
@Composable
fun AuthScreenContainer(
    modifier: Modifier = Modifier,
    centerContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleUi.colors.canvas)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = AuthFormMaxWidth)
                .fillMaxWidth()
                .then(
                    if (centerContent) {
                        Modifier.align(Alignment.Center)
                    } else {
                        Modifier
                            .fillMaxHeight()
                            .align(Alignment.TopCenter)
                    },
                )
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = AuthPageGutter, vertical = 24.dp),
            content = content,
        )
    }
}
