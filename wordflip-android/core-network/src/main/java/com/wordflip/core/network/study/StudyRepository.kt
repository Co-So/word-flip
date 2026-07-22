package com.wordflip.core.network.study

import com.wordflip.core.model.study.StudyGroupPayload
import com.wordflip.core.model.study.StudySessionReportRequest
import com.wordflip.core.model.study.StudySessionReportResponse
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.study.WordImagePayload
import com.wordflip.core.model.study.WordStainPayload
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.StudyApi
import com.wordflip.core.network.media.MediaUrlResolver
import java.time.Instant
import java.util.TimeZone

/**
 * 学习页数据编排：GET /study/groups/{id}、POST /study/sessions（P1-A16）。
 */
class StudyRepository(
    private val studyApi: StudyApi,
    private val apiErrorParser: ApiErrorParser,
    private val apiBaseUrl: String,
) {

    suspend fun loadStudyGroup(groupId: Int): Result<StudyGroupPayload> = apiCall {
        studyApi.getStudyGroup(groupId).withNormalizedWords()
    }

    /** Gson 对缺失的 image/stain 可能反序列化为 null；补齐 seed / showCnOnImage / filters；解析媒体相对路径 */
    private fun StudyGroupPayload.withNormalizedWords(): StudyGroupPayload = copy(
        words = words.map { word ->
            val image = word.image ?: WordImagePayload()
            val stain = word.stain ?: WordStainPayload()
            val transform = image.transform
            val safeFilters = (image.filters ?: transform?.filters)?.let {
                ImageFilters.safe(
                    brightness = it.brightness,
                    contrast = it.contrast,
                    saturate = it.saturate,
                    grayscale = it.grayscale,
                    sepia = it.sepia,
                )
            }
            word.copy(
                image = image.copy(
                    showCnOnImage = transform?.showCn ?: image.showCnOnImage,
                    filters = safeFilters,
                    transform = transform?.copy(filters = safeFilters ?: transform.filters),
                    imageUrl = MediaUrlResolver.resolve(apiBaseUrl, image.imageUrl),
                ),
                stain = stain.copy(
                    seed = stain.config?.seed ?: stain.seed,
                ),
            )
        },
    )

    suspend fun reportSession(
        groupId: Int,
        durationSec: Int,
        wordsViewed: Int,
    ): Result<StudySessionReportResponse> = apiCall {
        studyApi.reportSession(
            timezone = TimeZone.getDefault().id,
            request = StudySessionReportRequest(
                groupId = groupId,
                durationSec = durationSec,
                cardsViewed = wordsViewed,
                completedAt = Instant.now().toString(),
            ),
        )
    }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (throwable: Throwable) {
        Result.failure(Exception(apiErrorParser.parseMessage(throwable), throwable))
    }
}
