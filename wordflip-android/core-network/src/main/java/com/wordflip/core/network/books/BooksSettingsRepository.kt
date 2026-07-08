package com.wordflip.core.network.books

import com.wordflip.core.model.book.BooksPageData
import com.wordflip.core.model.book.GroupStrategy
import com.wordflip.core.model.book.SaveBooksSettingsRequest
import com.wordflip.core.model.book.SaveBooksSettingsResponse
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.BooksApi
import com.wordflip.core.network.api.SettingsApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 词书/设置业务编排：GET /books + GET/PUT /settings。
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
