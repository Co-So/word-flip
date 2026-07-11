package com.wordflip.core.network.api

import com.wordflip.core.model.book.DictionaryItem
import retrofit2.http.GET

/**
 * GET /dicts 内置词典目录。
 */
interface DictsApi {

    @GET("dicts")
    suspend fun listDictionaries(): List<DictionaryItem>
}
