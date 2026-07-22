package com.wordflip.core.model.group

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/** 分组卡片 JSON 必须使用新的双轨 FSRS 进度，不再依赖旧 mastery 字段。 */
class GroupCardsResponseTest {

    @Test
    fun responseWithoutLegacyMasteryCanBeConvertedForUi() {
        val json = """
            {
              "page": 1,
              "size": 20,
              "totalElements": 1,
              "totalPages": 1,
              "cards": [{
                "cardId": 11,
                "lexemeId": 22,
                "bookId": 33,
                "wordKey": "apple",
                "en": "apple",
                "phonetic": "/ˈæpəl/",
                "version": 1,
                "senses": [],
                "progress": {
                  "dictation": {"state":"new","dueAt":"2026-07-17T00:00:00Z","stability":0.0,"difficulty":0.0,"reps":0,"lapses":0},
                  "choice": {"state":"new","dueAt":"2026-07-17T00:00:00Z","stability":0.0,"difficulty":0.0,"reps":0,"lapses":0},
                  "displayHeatLevel": 0
                },
                "sourceMaterials": []
              }]
            }
        """.trimIndent()

        val response = Gson().fromJson(json, GroupWordsResponse::class.java)
        val item = response.words.single().toGroupWordItem()

        assertEquals(11L, item.summary.cardId)
        assertEquals(0, item.displayHeatLevel)
    }
}
