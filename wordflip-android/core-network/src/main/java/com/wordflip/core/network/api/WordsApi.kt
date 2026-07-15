package com.wordflip.core.network.api

import com.wordflip.core.model.study.WordCard
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GET /words/{wordKey}?dictId= 按词典查单词释义（详情抽屉临时切换）。
 */
interface WordsApi {

    @GET("words/{wordKey}")
    suspend fun lookupWord(
        @Path("wordKey") wordKey: String,
        @Query("dictId") dictId: String? = null,
    ): WordLookupResponse
}

/**
 * 按词典查询单词释义响应，对齐服务端 WordLookupResponse。
 */
data class WordLookupResponse(
    val wordKey: String,
    val en: String,
    val cn: String? = null,
    val pos: String? = null,
    val ph: String? = null,
    val enGloss: String? = null,
    val senses: List<com.wordflip.core.model.study.Sense> = emptyList(),
    val dictId: String,
    val dictName: String,
    val dictLocale: String,
)
