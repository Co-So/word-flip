package com.wordflip.core.network.media

import com.google.gson.Gson
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.model.media.StainBatchRequest
import com.wordflip.core.model.media.StainBatchResponse
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.media.StainUpdateRequest
import com.wordflip.core.model.media.WordImageResponse
import com.wordflip.core.model.media.WordStainResponse
import com.wordflip.core.model.study.WordImagePayload
import com.wordflip.core.model.study.WordStainPayload
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.ImagesApi
import com.wordflip.core.network.api.StainsApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 卡片图片与污渍数据编排：multipart 上传、transform PATCH、stain PUT/batch（P3）。
 */
class WordMediaRepository(
    private val imagesApi: ImagesApi,
    private val stainsApi: StainsApi,
    private val gson: Gson,
    private val apiErrorParser: ApiErrorParser,
    private val apiBaseUrl: String,
) {

    /**
     * 上传或替换卡片图片；transform JSON 含 showCn 与 filters（对齐 openapi）。
     */
    suspend fun uploadImage(
        cardId: Long,
        fileBytes: ByteArray,
        mimeType: String,
        transform: ImageTransform,
        filters: ImageFilters,
        showCn: Boolean,
    ): Result<WordImagePayload> = apiCall {
        val apiTransform = transform.copy(showCn = showCn, filters = filters)
        val transformJson = gson.toJson(apiTransform)
        val fileBody = fileBytes.toRequestBody(mimeType.toMediaType())
        val filePart = MultipartBody.Part.createFormData("file", "card.webp", fileBody)
        val transformPart = transformJson.toRequestBody("text/plain".toMediaType())
        imagesApi.uploadImage(cardId, filePart, transformPart).toPayload()
    }

    /** 仅更新 transform（不重传文件） */
    suspend fun patchImageTransform(
        cardId: Long,
        transform: ImageTransform,
        filters: ImageFilters,
        showCn: Boolean,
    ): Result<WordImagePayload> = apiCall {
        imagesApi.patchTransform(cardId, transform.copy(showCn = showCn, filters = filters)).toPayload()
    }

    suspend fun deleteImage(cardId: Long): Result<Unit> = apiCall {
        imagesApi.deleteImage(cardId)
    }

    suspend fun getStain(cardId: Long): Result<WordStainPayload> = apiCall {
        stainsApi.getStain(cardId).toPayload()
    }

    /** 换一个污渍（regenerate） */
    suspend fun regenerateStain(
        cardId: Long,
        allowedTypes: List<StainType> = StainType.entries,
    ): Result<WordStainPayload> = apiCall {
        stainsApi.updateStain(
            cardId,
            StainUpdateRequest(
                action = "regenerate",
                typeFilter = allowedTypes.map { it.toApiValue() },
            ),
        ).toPayload()
    }

    suspend fun setStainHidden(cardId: Long, hidden: Boolean): Result<WordStainPayload> = apiCall {
        stainsApi.updateStain(
            cardId,
            StainUpdateRequest(action = if (hidden) "set_hidden" else "set_visible"),
        ).toPayload()
    }

    suspend fun batchRegenerateStains(
        groupId: Int,
        allowedTypes: List<StainType>? = null,
    ): Result<StainBatchResponse> = apiCall {
        stainsApi.batchRegenerate(
            groupId,
            StainBatchRequest(typeFilter = allowedTypes?.map { it.toApiValue() }),
        )
    }

    private fun WordImageResponse.toPayload(): WordImagePayload {
        val t = transform
        val rawFilters = t?.filters
        val safeFilters = rawFilters?.let {
            ImageFilters.safe(
                brightness = it.brightness,
                contrast = it.contrast,
                saturate = it.saturate,
                grayscale = it.grayscale,
                sepia = it.sepia,
            )
        }
        return WordImagePayload(
            hasImage = hasImage,
            imageUrl = MediaUrlResolver.resolve(apiBaseUrl, imageUrl),
            showCnOnImage = t?.showCn ?: true,
            transform = t?.copy(filters = safeFilters),
            filters = safeFilters,
        )
    }

    private fun WordStainResponse.toPayload(): WordStainPayload = WordStainPayload(
        hidden = hidden,
        seed = config?.seed ?: 0L,
        config = config,
    )

    private fun StainType.toApiValue(): String = when (this) {
        StainType.COFFEE -> "coffee"
        StainType.INK -> "ink"
        StainType.HIGHLIGHT -> "highlight"
        StainType.CRAYON -> "crayon"
        StainType.RANDOM_LINE -> "random-line"
    }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (throwable: Throwable) {
        Result.failure(Exception(apiErrorParser.parseMessage(throwable), throwable))
    }
}
