package com.wordflip.core.network.api

import com.wordflip.core.model.study.StudyGroupPayload
import com.wordflip.core.model.study.StudySessionReportRequest
import com.wordflip.core.model.study.StudySessionReportResponse
import com.wordflip.core.model.today.TodayDashboard
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 今日仪表盘 API：GET /today（P1-B03~B07）。
 */
interface TodayApi {

    @GET("today")
    suspend fun getToday(
        @Header("X-Timezone") timezone: String,
    ): TodayDashboard
}

/**
 * 学习页 API：GET /study/groups/{groupId}、POST /study/sessions（P1-B10~B12）。
 */
interface StudyApi {

    @GET("study/groups/{groupId}")
    suspend fun getStudyGroup(@Path("groupId") groupId: Int): StudyGroupPayload

    @POST("study/sessions")
    suspend fun reportSession(
        @Header("X-Timezone") timezone: String,
        @Body request: StudySessionReportRequest,
    ): StudySessionReportResponse
}
