package com.wordflip.core.network.learning

import com.wordflip.core.model.learning.CreateLearningPlanRequest
import com.wordflip.core.model.learning.LearningCardDetail
import com.wordflip.core.model.learning.LearningPlan
import com.wordflip.core.model.learning.PatchLearningPlanRequest
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.LearningCardsApi
import com.wordflip.core.network.api.LearningPlansApi
import retrofit2.HttpException

/** Android 学习计划和卡片详情的唯一网络入口。 */
class LearningRepository(
    private val plansApi: LearningPlansApi,
    private val cardsApi: LearningCardsApi,
    private val apiErrorParser: ApiErrorParser,
) {
    /** 无当前计划时后端返回 404，客户端据此进入首次选书页。 */
    suspend fun currentPlan(): Result<LearningPlan?> = try {
        Result.success(plansApi.current())
    } catch (error: HttpException) {
        if (error.code() == 404) Result.success(null) else Result.failure(parsed(error))
    } catch (error: Throwable) {
        Result.failure(parsed(error))
    }

    suspend fun startBook(bookId: Long, dailyNewCardLimit: Int = 20): Result<LearningPlan> = apiCall {
        plansApi.create(CreateLearningPlanRequest(bookId, dailyNewCardLimit))
    }

    suspend fun switchPlan(planId: Long): Result<LearningPlan> = apiCall {
        plansApi.patch(PatchLearningPlanRequest(planId = planId))
    }

    suspend fun loadCard(cardId: Long): Result<LearningCardDetail> = apiCall {
        cardsApi.getCard(cardId)
    }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(parsed(error))
    }

    private fun parsed(error: Throwable): Exception =
        Exception(apiErrorParser.parseMessage(error), error)
}
