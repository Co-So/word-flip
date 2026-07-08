package com.wordflip.core.network.study

import com.wordflip.core.model.study.StudyGroupPayload
import com.wordflip.core.model.study.StudySessionReportRequest
import com.wordflip.core.model.study.StudySessionReportResponse
import com.wordflip.core.model.study.WordImagePayload
import com.wordflip.core.model.study.WordStainPayload
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.StudyApi
import java.time.Instant
import java.util.TimeZone

/**
 * 学习页数据编排：GET /study/groups/{id}、POST /study/sessions（P1-A16）。
 */
class StudyRepository(
    private val studyApi: StudyApi,
    private val apiErrorParser: ApiErrorParser,
) {

    suspend fun loadStudyGroup(groupId: Int): Result<StudyGroupPayload> = apiCall {
        studyApi.getStudyGroup(groupId).withNormalizedWords()
    }

    /** Gson 对缺失的 image/stain 可能反序列化为 null，补齐默认值避免 copy NPE */
    private fun StudyGroupPayload.withNormalizedWords(): StudyGroupPayload = copy(
        words = words.map { word ->
            word.copy(
                image = word.image ?: WordImagePayload(),
                stain = word.stain ?: WordStainPayload(),
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
                wordsViewed = wordsViewed,
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
