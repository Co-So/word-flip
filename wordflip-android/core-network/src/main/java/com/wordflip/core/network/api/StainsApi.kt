package com.wordflip.core.network.api

import com.wordflip.core.model.media.StainBatchRequest
import com.wordflip.core.model.media.StainBatchResponse
import com.wordflip.core.model.media.StainUpdateRequest
import com.wordflip.core.model.media.WordStainResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * 污渍 API：单卡配置绑定 cardId，批量接口绑定当前计划分组。
 */
interface StainsApi {

    @GET("learning/cards/{cardId}/stain")
    suspend fun getStain(
        @Path("cardId") cardId: Long,
    ): WordStainResponse

    @PUT("learning/cards/{cardId}/stain")
    suspend fun updateStain(
        @Path("cardId") cardId: Long,
        @Body request: StainUpdateRequest,
    ): WordStainResponse

    @POST("groups/{groupId}/stains/batch")
    suspend fun batchRegenerate(
        @Path("groupId") groupId: Int,
        @Body request: StainBatchRequest = StainBatchRequest(),
    ): StainBatchResponse
}
