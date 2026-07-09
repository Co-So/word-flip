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
 * 卡片图片 API：GET/POST/PATCH/DELETE /words/{wordKey}/image（P3）。
 */
interface ImagesApi {

    @GET("words/{wordKey}/image")
    suspend fun getImage(
        @Path("wordKey") wordKey: String,
    ): WordImageResponse

    @Multipart
    @POST("words/{wordKey}/image")
    suspend fun uploadImage(
        @Path("wordKey") wordKey: String,
        @Part file: MultipartBody.Part,
        @Part("transform") transform: RequestBody,
    ): WordImageResponse

    @PATCH("words/{wordKey}/image")
    suspend fun patchTransform(
        @Path("wordKey") wordKey: String,
        @Body transform: ImageTransform,
    ): WordImageResponse

    @DELETE("words/{wordKey}/image")
    suspend fun deleteImage(
        @Path("wordKey") wordKey: String,
    )
}
