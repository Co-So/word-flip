package com.wordflip.core.network.api

import com.wordflip.core.model.book.BookImportConfirmRequest
import com.wordflip.core.model.book.BookImportConfirmResponse
import com.wordflip.core.model.book.BookImportPreviewResponse
import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.BookListResponse
import com.wordflip.core.model.book.BookWordsResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 词书 API：列表/详情/词条、导入、删除。
 */
interface BooksApi {

    @GET("books")
    suspend fun listBooks(): BookListResponse

    @GET("books/{bookId}")
    suspend fun getBook(@Path("bookId") bookId: Long): BookItem

    @GET("books/{bookId}/words")
    suspend fun listBookWords(
        @Path("bookId") bookId: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): BookWordsResponse

    @Multipart
    @POST("books/import/preview")
    suspend fun previewImport(@Part file: MultipartBody.Part): BookImportPreviewResponse

    @POST("books/import")
    suspend fun confirmImport(@Body request: BookImportConfirmRequest): BookImportConfirmResponse

    @DELETE("books/{bookId}")
    suspend fun deleteBook(@Path("bookId") bookId: Long)
}
