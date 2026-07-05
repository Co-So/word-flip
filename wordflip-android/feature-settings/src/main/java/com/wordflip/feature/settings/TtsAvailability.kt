package com.wordflip.feature.settings

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TTS_CHECK_TIMEOUT_MS = 3_000L

/**
 * 检测系统文字转语音引擎是否可用（设置页开启自动发音前提示用）。
 */
suspend fun checkTtsAvailable(context: Context): Boolean {
    return withTimeoutOrNull(TTS_CHECK_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context.applicationContext) { status ->
                val available = status == TextToSpeech.SUCCESS
                tts?.shutdown()
                if (continuation.isActive) {
                    continuation.resume(available)
                }
            }
            continuation.invokeOnCancellation {
                tts?.shutdown()
            }
        }
    } ?: false
}
