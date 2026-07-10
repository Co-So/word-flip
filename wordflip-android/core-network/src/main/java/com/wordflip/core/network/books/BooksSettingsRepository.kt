package com.wordflip.core.network.books

import com.wordflip.core.model.book.BookImportConfirmRequest
import com.wordflip.core.model.book.BookImportConfirmResponse
import com.wordflip.core.model.book.BookImportPreviewResponse
import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.BookWordsResponse
import com.wordflip.core.model.book.BooksPageData
import com.wordflip.core.model.book.GroupStrategy
import com.wordflip.core.model.book.SaveBooksSettingsRequest
import com.wordflip.core.model.book.SaveBooksSettingsResponse
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.BooksApi
import com.wordflip.core.network.api.SettingsApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 词书/设置业务编排：列表、详情、导入、删除、PUT /settings。
 */
class BooksSettingsRepository(
    private val booksApi: BooksApi,
    private val settingsApi: SettingsApi,
    private val apiErrorParser: ApiErrorParser,
) {

    suspend fun loadBooksPage(): Result<BooksPageData> = apiCall {
        coroutineScope {
            val booksDeferred = async { booksApi.listBooks() }
            val settingsDeferred = async { settingsApi.getSettings() }
            BooksPageData(
                books = booksDeferred.await().books,
                settings = settingsDeferred.await(),
            )
        }
    }

    suspend fun getBook(bookId: Long): Result<BookItem> = apiCall {
        booksApi.getBook(bookId)
    }

    suspend fun listBookWords(bookId: Long, page: Int, size: Int = 50): Result<BookWordsResponse> =
        apiCall { booksApi.listBookWords(bookId, page, size) }

    suspend fun previewImport(fileBytes: ByteArray, fileName: String, mimeType: String): Result<BookImportPreviewResponse> =
        apiCall {
            val body = fileBytes.toRequestBody(mimeType.toMediaType())
            val part = MultipartBody.Part.createFormData("file", fileName, body)
            booksApi.previewImport(part)
        }

    suspend fun confirmImport(previewToken: String, name: String): Result<BookImportConfirmResponse> =
        apiCall {
            booksApi.confirmImport(BookImportConfirmRequest(previewToken = previewToken, name = name))
        }

    suspend fun deleteBook(bookId: Long): Result<Unit> = apiCall {
        booksApi.deleteBook(bookId)
    }

    suspend fun saveBooksSettings(
        bookIds: List<Long>,
        groupSize: Int,
        groupStrategy: GroupStrategy,
        regroup: Boolean = false,
    ): Result<SaveBooksSettingsResponse> =
        apiCall {
            settingsApi.saveSettings(
                SaveBooksSettingsRequest(
                    bookIds = bookIds,
                    groupSize = groupSize,
                    groupStrategy = groupStrategy,
                    regroup = regroup,
                ),
            )
        }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (throwable: Throwable) {
        Result.failure(Exception(apiErrorParser.parseMessage(throwable), throwable))
    }
}
