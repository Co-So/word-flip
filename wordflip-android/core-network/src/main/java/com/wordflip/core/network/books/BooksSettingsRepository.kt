package com.wordflip.core.network.books

import com.wordflip.core.model.book.BookCardsPage
import com.wordflip.core.model.book.BookImportConfirmRequest
import com.wordflip.core.model.book.BookImportConfirmResponse
import com.wordflip.core.model.book.BookImportPreviewResponse
import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.BooksPageData
import com.wordflip.core.model.learning.CreateLearningPlanRequest
import com.wordflip.core.model.study.WordSummary
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.BooksApi
import com.wordflip.core.network.api.LearningCardsApi
import com.wordflip.core.network.api.LearningPlansApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/** 词书列表、导入与单一当前学习计划编排。 */
class BooksSettingsRepository(
    private val booksApi: BooksApi,
    private val plansApi: LearningPlansApi,
    private val cardsApi: LearningCardsApi,
    private val apiErrorParser: ApiErrorParser,
) {
    suspend fun loadBooksPage(): Result<BooksPageData> = apiCall {
        coroutineScope {
            val books = async { booksApi.listBooks().books }
            val currentPlan = async {
                try {
                    plansApi.current()
                } catch (error: HttpException) {
                    if (error.code() == 404) null else throw error
                }
            }
            BooksPageData(books.await(), currentPlan.await())
        }
    }

    suspend fun getBook(bookId: Long): Result<BookItem> = apiCall { booksApi.getBook(bookId) }

    suspend fun listBookCards(bookId: Long, page: Int, size: Int = 50): Result<BookCardsPage> = apiCall {
        val response = cardsApi.listBookCards(bookId, page, size)
        BookCardsPage(
            page = response.page,
            size = response.size,
            totalElements = response.totalElements,
            totalPages = response.totalPages,
            cards = response.cards.map { card ->
                val primary = card.senses.firstOrNull { it.primary } ?: card.senses.firstOrNull()
                WordSummary(
                    wordKey = card.wordKey,
                    en = card.en,
                    cn = primary?.cn,
                    pos = primary?.pos,
                    ph = card.phonetic,
                    enGloss = primary?.enGloss,
                    senses = card.senses,
                    cardId = card.cardId,
                    lexemeId = card.lexemeId,
                    bookId = card.bookId,
                    version = card.version,
                )
            },
        )
    }

    suspend fun startBook(bookId: Long): Result<Unit> = apiCall {
        plansApi.create(CreateLearningPlanRequest(bookId))
        Unit
    }

    suspend fun previewImport(fileBytes: ByteArray, fileName: String, mimeType: String): Result<BookImportPreviewResponse> =
        apiCall {
            val body = fileBytes.toRequestBody(mimeType.toMediaType())
            val part = MultipartBody.Part.createFormData("file", fileName, body)
            booksApi.previewImport(part)
        }

    suspend fun confirmImport(previewToken: String, name: String): Result<BookImportConfirmResponse> = apiCall {
        booksApi.confirmImport(BookImportConfirmRequest(previewToken = previewToken, name = name))
    }

    suspend fun deleteBook(bookId: Long): Result<Unit> = apiCall { booksApi.deleteBook(bookId) }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(Exception(apiErrorParser.parseMessage(error), error))
    }
}
