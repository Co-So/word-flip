package com.wordflip.core.network.api

import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.model.media.WordImageResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * 卡片图片 API：图片严格绑定词书学习卡。
 */
interface ImagesApi {

    @GET("learning/cards/{cardId}/image")
    suspend fun getImage(
        @Path("cardId") cardId: Long,
    ): WordImageResponse

    @Multipart
    @POST("learning/cards/{cardId}/image")
    suspend fun uploadImage(
        @Path("cardId") cardId: Long,
        @Part file: MultipartBody.Part,
        @Part("transform") transform: RequestBody,
    ): WordImageResponse

    @PATCH("learning/cards/{cardId}/image")
    suspend fun patchTransform(
        @Path("cardId") cardId: Long,
        @Body transform: ImageTransform,
    ): WordImageResponse

    @DELETE("learning/cards/{cardId}/image")
    suspend fun deleteImage(
        @Path("cardId") cardId: Long,
    )
}
