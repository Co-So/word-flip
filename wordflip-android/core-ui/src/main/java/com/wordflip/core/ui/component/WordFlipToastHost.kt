package com.wordflip.core.ui.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Toast 控制器，基于 Snackbar 实现底部居中反馈（A-17）。
 * 相同文案短时内去重，避免连点按钮重复弹出提示。
 */
class WordFlipToastController internal constructor(
    private val snackbarHostState: SnackbarHostState,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private var lastMessage: String? = null
    private var lastShownAtMs: Long = 0L
    private var showJob: Job? = null

    fun show(message: String) {
        if (message.isBlank()) return
        val now = System.currentTimeMillis()
        // 与 Snackbar Short 时长对齐：相同文案 2s 内不重复展示
        if (message == lastMessage && now - lastShownAtMs < DEDUPE_INTERVAL_MS) {
            return
        }
        lastMessage = message
        lastShownAtMs = now
        showJob?.cancel()
        showJob = scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
        }
    }

    private companion object {
        const val DEDUPE_INTERVAL_MS = 2_000L
    }
}

@Composable
fun rememberWordFlipToast(): Pair<SnackbarHostState, WordFlipToastController> {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val controller = remember(snackbarHostState, scope) {
        WordFlipToastController(snackbarHostState, scope)
    }
    return snackbarHostState to controller
}

/** Snackbar 宿主；可叠在全屏编辑器之上（modifier + zIndex） */
@Composable
fun WordFlipToastHost(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = snackbarHostState, modifier = modifier)
}
