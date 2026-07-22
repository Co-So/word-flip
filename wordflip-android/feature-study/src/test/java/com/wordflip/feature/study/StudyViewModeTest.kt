package com.wordflip.feature.study

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyViewModeTest {
    @Test
    fun `空值默认使用焦点加缩略轨道`() {
        assertEquals(StudyViewMode.HYBRID, StudyViewMode.fromStorage(null))
    }

    @Test
    fun `持久化值可恢复三种模式`() {
        StudyViewMode.entries.forEach { mode ->
            assertEquals(mode, StudyViewMode.fromStorage(mode.storageValue))
        }
    }

    @Test
    fun `未知值安全回退默认模式`() {
        assertEquals(StudyViewMode.HYBRID, StudyViewMode.fromStorage("unknown"))
    }
}
