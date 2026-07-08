package com.wordflip.core.network.today

import com.wordflip.core.model.today.TodayDashboard
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.TodayApi
import java.util.TimeZone

/**
 * 今日页数据编排：GET /today + X-Timezone。
 */
class TodayRepository(
    private val todayApi: TodayApi,
    private val apiErrorParser: ApiErrorParser,
) {

    suspend fun loadDashboard(): Result<TodayDashboard> = apiCall {
        todayApi.getToday(timezone = TimeZone.getDefault().id)
    }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (throwable: Throwable) {
        Result.failure(Exception(apiErrorParser.parseMessage(throwable), throwable))
    }
}
