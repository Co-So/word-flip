package com.wordflip.core.model.study

import org.junit.Assert.assertEquals
import org.junit.Test

/** 学习卡展示义必须来自词书主考义，不依赖已取消的顶层 cn 字段。 */
class LearningCardMeaningTest {

    @Test
    fun modelsUsePrimarySenseWhenFlatMeaningIsAbsent() {
        val senses = listOf(
            Sense(cn = "完整资料释义", primary = false, sortOrder = 0),
            Sense(cn = "词书主考义", primary = true, sortOrder = 1),
        )
        val card = WordCard(wordKey = "apple", en = "apple", senses = senses)
        val summary = WordSummary(wordKey = "apple", en = "apple", senses = senses)

        assertEquals("词书主考义", card.displayMeaning())
        assertEquals("词书主考义", summary.displayMeaning())
    }
}
