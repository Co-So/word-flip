package com.wordflip.core.model.fake

import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.model.media.StainConfig
import com.wordflip.core.model.media.StainGenerator
import com.wordflip.core.model.media.StainMode
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.media.StoredWordImage
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.model.study.WordImagePayload
import com.wordflip.core.model.study.WordStainPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * P3 Mock 媒体仓库：卡拍图片与污渍跨学习/卡拍/分组详情共享（REQ-SNAP / REQ-STAIN）。
 * 接 API 后由 Repository 替换。
 */
object MockWordMediaStore {

    private val images = mutableMapOf<String, StoredWordImage>()
    private val stainOverrides = mutableMapOf<String, WordStainPayload>()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** 将 Mock 媒体叠加到 WordCard（不改变 mastery 等字段） */
    fun applyToWordCard(card: WordCard): WordCard {
        val storedImage = images[card.wordKey]
        val stain = stainOverrides[card.wordKey] ?: card.stain
        val image = if (storedImage != null) {
            WordImagePayload(
                hasImage = true,
                imageUrl = storedImage.localUri,
                showCnOnImage = storedImage.showCnOnImage,
                transform = storedImage.transform,
                filters = storedImage.filters,
            )
        } else {
            card.image
        }
        return card.copy(image = image, stain = stain)
    }

    fun applyToWords(words: List<WordCard>): List<WordCard> =
        words.map(::applyToWordCard)

    /** P3-A06 Mock 保存图片 */
    fun saveImage(
        wordKey: String,
        localUri: String,
        transform: ImageTransform = ImageTransform(),
        filters: ImageFilters = ImageFilters(),
        showCnOnImage: Boolean = true,
    ) {
        images[wordKey] = StoredWordImage(
            localUri = localUri,
            transform = transform,
            filters = filters,
            showCnOnImage = showCnOnImage,
        )
        bump()
    }

    /** P3-A06 Mock 清除图片 */
    fun clearImage(wordKey: String) {
        images.remove(wordKey)
        bump()
    }

    fun getImage(wordKey: String): StoredWordImage? = images[wordKey]

    /** REQ-STAIN-4：换一个污渍 */
    fun regenerateStain(
        wordKey: String,
        mode: StainMode = StainMode.RANDOM,
        allowedTypes: List<StainType> = StainType.entries,
    ) {
        val config = StainGenerator.generate(
            wordKey = wordKey,
            mode = mode,
            allowedTypes = allowedTypes,
            overrideSeed = Random.nextLong(),
        )
        stainOverrides[wordKey] = WordStainPayload(
            hidden = false,
            seed = config.seed,
            config = config,
        )
        bump()
    }

    /** REQ-STAIN-5：隐藏污渍 */
    fun hideStain(wordKey: String) {
        val current = stainOverrides[wordKey]
        stainOverrides[wordKey] = (current ?: defaultStain(wordKey)).copy(hidden = true)
        bump()
    }

    /** 显示污渍（取消隐藏） */
    fun showStain(wordKey: String) {
        val current = stainOverrides[wordKey] ?: defaultStain(wordKey)
        stainOverrides[wordKey] = current.copy(hidden = false)
        bump()
    }

    fun toggleShowCnOnImage(wordKey: String) {
        val stored = images[wordKey] ?: return
        images[wordKey] = stored.copy(showCnOnImage = !stored.showCnOnImage)
        bump()
    }

    /** P3-A10：批量生成组内污渍 */
    fun batchRegenerateStains(
        wordKeys: List<String>,
        mode: StainMode = StainMode.RANDOM,
        allowedTypes: List<StainType> = StainType.entries,
    ) {
        wordKeys.forEach { key ->
            regenerateStain(key, mode, allowedTypes)
        }
    }

    /** P3-A10：批量隐藏/显示 */
    fun batchSetStainHidden(wordKeys: List<String>, hidden: Boolean) {
        wordKeys.forEach { key ->
            if (hidden) hideStain(key) else showStain(key)
        }
    }

    fun stainFor(wordKey: String, fallback: WordStainPayload): WordStainPayload =
        stainOverrides[wordKey] ?: fallback

    private fun defaultStain(wordKey: String): WordStainPayload {
        val seed = StainGenerator.stableSeed(wordKey)
        val config = StainGenerator.generate(wordKey, overrideSeed = seed)
        return WordStainPayload(hidden = false, seed = seed, config = config)
    }

    private fun bump() {
        _revision.value += 1
    }
}
