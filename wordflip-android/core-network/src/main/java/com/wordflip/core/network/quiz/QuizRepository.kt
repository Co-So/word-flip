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
 * createSession 透传 groupIds / questionTypes / launchMode；选择题用 selectedKey。
 */
class QuizRepository(
    private val quizApi: QuizApi,
    private val apiErrorParser: ApiErrorParser,
) {

    suspend fun createSession(
        source: QuizSource,
        groupId: Int?,
        questionLimit: Int,
        groupIds: List<Int>? = null,
        questionTypes: List<String>? = null,
        launchMode: String? = null,
    ): Result<QuizSessionCreated> = apiCall {
        quizApi.createSession(
            timezone = TimeZone.getDefault().id,
            request = CreateQuizSessionRequest(
                source = source.name.lowercase(),
                groupId = groupId,
                groupIds = groupIds,
                questionLimit = questionLimit.coerceIn(1, 50),
                questionTypes = questionTypes,
                launchMode = launchMode,
            ),
        )
    }

    suspend fun submitAnswer(
        sessionId: String,
        requestId: String,
        questionIndex: Int,
        answer: String? = null,
        selectedKey: String? = null,
    ): Result<AnswerResult> = apiCall {
        quizApi.submitAnswer(
            sessionId = sessionId,
            timezone = TimeZone.getDefault().id,
            request = SubmitAnswerRequest(
                requestId = requestId,
                questionIndex = questionIndex,
                answer = answer,
                selectedKey = selectedKey,
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
