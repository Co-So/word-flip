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
 * 污渍 API：GET/PUT /words/{wordKey}/stain、POST /groups/{groupId}/stains/batch（P3）。
 */
interface StainsApi {

    @GET("words/{wordKey}/stain")
    suspend fun getStain(
        @Path("wordKey") wordKey: String,
    ): WordStainResponse

    @PUT("words/{wordKey}/stain")
    suspend fun updateStain(
        @Path("wordKey") wordKey: String,
        @Body request: StainUpdateRequest,
    ): WordStainResponse

    @POST("groups/{groupId}/stains/batch")
    suspend fun batchRegenerate(
        @Path("groupId") groupId: Int,
        @Body request: StainBatchRequest = StainBatchRequest(),
    ): StainBatchResponse
}
