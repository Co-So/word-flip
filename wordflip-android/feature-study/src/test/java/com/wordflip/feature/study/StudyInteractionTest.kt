package com.wordflip.feature.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyInteractionTest {
    @Test
    fun `开关开启时正面翻到背面发音`() {
        assertTrue(shouldAutoSpeakAfterFlip(wasFlipped = false, autoSpeakEnabled = true))
    }

    @Test
    fun `开关开启时背面翻回正面发音`() {
        assertTrue(shouldAutoSpeakAfterFlip(wasFlipped = true, autoSpeakEnabled = true))
    }

    @Test
    fun `关闭自动发音时正反两面均不发音`() {
        assertFalse(shouldAutoSpeakAfterFlip(wasFlipped = false, autoSpeakEnabled = false))
        assertFalse(shouldAutoSpeakAfterFlip(wasFlipped = true, autoSpeakEnabled = false))
    }

    @Test
    fun `快速连续翻转两次均发音且每次反转翻面状态`() {
        var isFlipped = false
        val flipStates = mutableListOf<Boolean>()
        val autoSpeakEvents = buildList<Boolean> {
            repeat(2) {
                val result = reduceStudyFlip(
                    wasFlipped = isFlipped,
                    autoSpeakEnabled = true,
                )
                isFlipped = result.isFlipped
                flipStates += isFlipped
                add(result.shouldAutoSpeak)
            }
        }

        assertEquals(listOf(true, true), autoSpeakEvents)
        assertEquals(listOf(true, false), flipStates)
    }
}
