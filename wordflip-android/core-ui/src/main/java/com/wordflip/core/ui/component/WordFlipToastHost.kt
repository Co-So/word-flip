package com.wordflip.core.ui.component

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Toast 控制器，基于 Snackbar 实现底部居中反馈（A-17）。
 */
class WordFlipToastController internal constructor(
    private val snackbarHostState: SnackbarHostState,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    fun show(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = false,
                duration = androidx.compose.material3.SnackbarDuration.Short,
            )
        }
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

/** Snackbar 宿主，置于 Scaffold snackbarHost 槽位 */
@Composable
fun WordFlipToastHost(
    snackbarHostState: SnackbarHostState,
) {
    SnackbarHost(hostState = snackbarHostState)
}
