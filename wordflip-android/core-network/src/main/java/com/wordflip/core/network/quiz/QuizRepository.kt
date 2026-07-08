package com.wordflip.core.network.quiz

import com.wordflip.core.model.quiz.AnswerResult
import com.wordflip.core.model.quiz.CreateQuizSessionRequest
import com.wordflip.core.model.quiz.QuizResultPayload
import com.wordflip.core.model.quiz.QuizSessionCreated
import com.wordflip.core.model.quiz.QuizSource
import com.wordflip.core.model.quiz.SubmitAnswerRequest
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.QuizApi
import java.util.TimeZone

/**
 * 测验页数据编排：创建 session、提交答案、拉取结果（P2-A12）。
 */
class QuizRepository(
    private val quizApi: QuizApi,
    private val apiErrorParser: ApiErrorParser,
) {

    suspend fun createSession(
        source: QuizSource,
        groupId: Int?,
        questionLimit: Int,
    ): Result<QuizSessionCreated> = apiCall {
        quizApi.createSession(
            timezone = TimeZone.getDefault().id,
            request = CreateQuizSessionRequest(
                source = source.name.lowercase(),
                groupId = groupId,
                questionLimit = questionLimit.coerceIn(1, 50),
            ),
        )
    }

    suspend fun submitAnswer(
        sessionId: String,
        questionIndex: Int,
        answer: String,
    ): Result<AnswerResult> = apiCall {
        quizApi.submitAnswer(
            sessionId = sessionId,
            timezone = TimeZone.getDefault().id,
            request = SubmitAnswerRequest(
                questionIndex = questionIndex,
                answer = answer,
            ),
        )
    }

    suspend fun loadResult(sessionId: String): Result<QuizResultPayload> = apiCall {
        quizApi.getResult(sessionId)
    }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (throwable: Throwable) {
        Result.failure(Exception(apiErrorParser.parseMessage(throwable), throwable))
    }
}
