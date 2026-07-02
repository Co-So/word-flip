package com.wordflip.feature.study

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 学习页 TTS：卡片点击固定 1.0x；详情页语速独立可调（REQ-STUDY-17）。
 */
class StudyTtsHelper(
    context: Context,
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    /** 卡片点击朗读，不受详情页语速调节影响 */
    private val cardSpeechRate = 1.0f

    /** 详情抽屉朗读语速，0.5x ~ 2.0x */
    private var detailSpeechRate = 1.0f

    /** TTS 异步初始化完成前的待播队列 */
    private var pendingCardWord: String? = null
    private var pendingDetailWord: String? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /** 是否由详情页触发的朗读（用于音节流动动画，卡片点击不触发） */
    private val _isDetailSpeaking = MutableStateFlow(false)
    val isDetailSpeaking: StateFlow<Boolean> = _isDetailSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) return@TextToSpeech
            configureLanguage()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    _isDetailSpeaking.value = utteranceId?.startsWith("detail-") == true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _isDetailSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _isDetailSpeaking.value = false
                }
            })
            flushPending()
        }
    }

    /** 卡片点击：固定 1.0x，不 Toast 音标 */
    fun speakForCard(word: String) {
        if (!ready) {
            pendingCardWord = word
            return
        }
        speakInternal(word, cardSpeechRate, utteranceTag = "card")
    }

    /** 详情页朗读：使用 detailSpeechRate */
    fun speakForDetail(word: String) {
        if (!ready) {
            pendingDetailWord = word
            return
        }
        speakInternal(word, detailSpeechRate, utteranceTag = "detail")
    }

    fun adjustDetailRate(delta: Float) {
        detailSpeechRate = (detailSpeechRate + delta).coerceIn(0.5f, 2.0f)
    }

    val detailRate: Float get() = detailSpeechRate

    private fun configureLanguage() {
        val engine = tts ?: return
        when (engine.setLanguage(Locale.US)) {
            TextToSpeech.LANG_MISSING_DATA,
            TextToSpeech.LANG_NOT_SUPPORTED,
            -> engine.setLanguage(Locale.ENGLISH)
        }
    }

    private fun flushPending() {
        pendingCardWord?.let { word ->
            pendingCardWord = null
            speakInternal(word, cardSpeechRate, utteranceTag = "card")
        }
        pendingDetailWord?.let { word ->
            pendingDetailWord = null
            speakInternal(word, detailSpeechRate, utteranceTag = "detail")
        }
    }

    private fun speakInternal(word: String, rate: Float, utteranceTag: String) {
        val engine = tts ?: return
        engine.setSpeechRate(rate)
        val utteranceId = "$utteranceTag-$word"
        val params = Bundle()
        engine.speak(word, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun shutdown() {
        pendingCardWord = null
        pendingDetailWord = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
