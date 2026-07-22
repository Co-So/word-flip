package com.wordflip.feature.today

import com.wordflip.core.model.today.RecentGroup
import com.wordflip.core.model.today.RecommendedStudy
import com.wordflip.core.model.today.TodayDashboard

/** 今日页主卡的展示内容。 */
sealed interface TodayPrimaryCard {
    data class Recommended(val study: RecommendedStudy) : TodayPrimaryCard

    data class Recent(val group: RecentGroup) : TodayPrimaryCard

    data object Empty : TodayPrimaryCard
}

/** 主卡只做展示回退，不重新计算服务端推荐优先级。 */
fun resolveTodayPrimaryCard(dashboard: TodayDashboard): TodayPrimaryCard {
    val recommended = dashboard.recommendedStudy
    return when {
        recommended != null -> TodayPrimaryCard.Recommended(recommended)
        dashboard.recentGroups.isNotEmpty() -> TodayPrimaryCard.Recent(dashboard.recentGroups.first())
        else -> TodayPrimaryCard.Empty
    }
}
