package com.wordflip.core.network.api

import com.wordflip.core.model.quiz.AnswerResult
import com.wordflip.core.model.quiz.CreateQuizSessionRequest
import com.wordflip.core.model.quiz.QuizResultPayload
import com.wordflip.core.model.quiz.QuizSessionCreated
import com.wordflip.core.model.quiz.SubmitAnswerRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 默写测验 API：POST /quiz/sessions、answer、GET result（P2-A12）。
 */
interface QuizApi {

    @POST("quiz/sessions")
    suspend fun createSession(
        @Header("X-Timezone") timezone: String,
        @Body request: CreateQuizSessionRequest,
    ): QuizSessionCreated

    @POST("quiz/sessions/{sessionId}/answer")
    suspend fun submitAnswer(
        @Path("sessionId") sessionId: String,
        @Header("X-Timezone") timezone: String,
        @Body request: SubmitAnswerRequest,
    ): AnswerResult

    @GET("quiz/sessions/{sessionId}/result")
    suspend fun getResult(
        @Path("sessionId") sessionId: String,
    ): QuizResultPayload
}
