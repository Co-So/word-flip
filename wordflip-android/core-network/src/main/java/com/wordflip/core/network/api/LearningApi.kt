package com.wordflip.core.network.api

import com.wordflip.core.model.learning.BookCardsResponse
import com.wordflip.core.model.learning.CreateLearningPlanRequest
import com.wordflip.core.model.learning.LearningCardDetail
import com.wordflip.core.model.learning.LearningPlan
import com.wordflip.core.model.learning.PatchLearningPlanRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** 唯一当前主词书与历史计划 API。 */
interface LearningPlansApi {
    @POST("learning-plans")
    suspend fun create(@Body request: CreateLearningPlanRequest): LearningPlan

    @GET("learning-plans/current")
    suspend fun current(): LearningPlan

    @PATCH("learning-plans/current")
    suspend fun patch(@Body request: PatchLearningPlanRequest): LearningPlan
}

/** 词书发布卡片与卡片详情 API。 */
interface LearningCardsApi {
    @GET("books/{bookId}/cards")
    suspend fun listBookCards(
        @Path("bookId") bookId: Long,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50,
    ): BookCardsResponse

    @GET("learning/cards/{cardId}")
    suspend fun getCard(@Path("cardId") cardId: Long): LearningCardDetail
}
