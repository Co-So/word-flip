package com.wordflip.feature.auth

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch

/**
 * 获焦时将输入框滚入可视区（配合 AuthScreenContainer 的 imePadding + verticalScroll）。
 * onBlur 仅在用户曾获焦后再失焦时触发，避免首帧 isFocused=false 误标 touched。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.authScrollOnFocus(onBlur: () -> Unit = {}): Modifier {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var hadFocus by remember { mutableStateOf(false) }
    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                hadFocus = true
                scope.launch {
                    bringIntoViewRequester.bringIntoView()
                }
            } else if (hadFocus) {
                hadFocus = false
                onBlur()
            }
        }
}
