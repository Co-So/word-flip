package com.wordflip.feature.quiz

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 测验巩固阶段 TTS：答错后朗读英文正确答案，支持语速调节与朗读状态（对齐学习详情抽屉）。
 */
class QuizTtsHelper(
    context: Context,
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingWord: String? = null

    /** 朗读语速，0.5x ~ 2.0x（REQ-STUDY-17 详情页同款范围） */
    private var speechRate = 1.0f

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) return@TextToSpeech
            configureLanguage()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }
            })
            pendingWord?.let { word ->
                pendingWord = null
                speakInternal(word)
            }
        }
    }

    /** 朗读英文单词；引擎未就绪时入队等待 */
    fun speak(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        if (!ready) {
            pendingWord = trimmed
            return
        }
        speakInternal(trimmed)
    }

    fun adjustRate(delta: Float) {
        speechRate = (speechRate + delta).coerceIn(0.5f, 2.0f)
    }

    fun resetRate() {
        speechRate = 1.0f
    }

    val rate: Float get() = speechRate

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        pendingWord = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        _isSpeaking.value = false
    }

    private fun configureLanguage() {
        val engine = tts ?: return
        when (engine.setLanguage(Locale.US)) {
            TextToSpeech.LANG_MISSING_DATA,
            TextToSpeech.LANG_NOT_SUPPORTED,
            -> engine.setLanguage(Locale.ENGLISH)
        }
    }

    private fun speakInternal(word: String) {
        val engine = tts ?: return
        engine.setSpeechRate(speechRate)
        engine.speak(word, TextToSpeech.QUEUE_FLUSH, Bundle(), "quiz-$word")
    }
}
