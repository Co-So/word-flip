package com.wordflip.core.network.api

import com.wordflip.core.model.book.BookListResponse
import retrofit2.http.GET

/**
 * 词书 API（GET /books）。
 */
interface BooksApi {

    @GET("books")
    suspend fun listBooks(): BookListResponse
}
