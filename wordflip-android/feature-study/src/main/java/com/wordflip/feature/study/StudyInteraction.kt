package com.wordflip.feature.study

/** 每次翻卡都按自动朗读开关决定是否发音，与提交前卡面无关。 */
fun shouldAutoSpeakAfterFlip(
    wasFlipped: Boolean,
    autoSpeakEnabled: Boolean,
): Boolean = autoSpeakEnabled

/** 单次翻卡提交的纯状态结果，供连续事件按最新状态顺序归约。 */
internal data class StudyFlipResult(
    val isFlipped: Boolean,
    val shouldAutoSpeak: Boolean,
)

/** 根据提交前状态计算下一面与本次自动发音决策，不直接修改业务状态。 */
internal fun reduceStudyFlip(
    wasFlipped: Boolean,
    autoSpeakEnabled: Boolean,
): StudyFlipResult = StudyFlipResult(
    isFlipped = !wasFlipped,
    shouldAutoSpeak = shouldAutoSpeakAfterFlip(wasFlipped, autoSpeakEnabled),
)
