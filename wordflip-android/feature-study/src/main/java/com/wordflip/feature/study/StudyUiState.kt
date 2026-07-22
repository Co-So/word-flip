package com.wordflip.feature.study

import com.wordflip.core.model.study.StudyGroupPayload
import com.wordflip.core.model.study.WordCard

/** 学习页 UI 状态 */
sealed interface StudyUiState {
    data object Loading : StudyUiState

    /**
     * 打乱动画单卡视觉变换：graphicsLayer 使用的位移/旋转/缩放。
     * 视觉层与数据顺序彻底解耦，由 wordKey 驱动。
     */
    data class ShuffleVisual(
        val tx: Float,
        val ty: Float,
        val rotation: Float,
        val scale: Float,
    )

    data class Content(
        val payload: StudyGroupPayload,
        val orderedWords: List<WordCard>,
        val flipStates: Map<String, Boolean>,
        val isShuffling: Boolean,
        val shufflePhase: ShufflePhase = ShufflePhase.None,
        val shuffleEpoch: Int = 0,
        /** 每卡视觉变换状态：收拢→发牌全程由 visuals 驱动，替代旧索引映射 */
        val shuffleVisuals: Map<String, ShuffleVisual> = emptyMap(),
        /**
         * 发牌起点补偿偏移（wordKey → graphicsLayer tx,ty）。
         * 在 Dealing 阶段开始时立即重排 orderedWords 后，用此偏移保证卡片视觉位置仍停留在牌堆中心，
         * 随后动画从该偏移插值到 (0,0)，实现“发完牌即已打乱、无闪动”的效果。
         */
        val shuffleDealStartOffsets: Map<String, Pair<Float, Float>> = emptyMap(),
        val shuffleMotions: Map<String, ShuffleMotion> = emptyMap(),
        /** 打乱开始时捕获的视口中心，供收拢/发牌位移 */
        val shuffleViewportAnchor: ShuffleViewportAnchor? = null,
        /** 打乱结束后短暂抑制 LazyGrid item 动画，避免闪烁 */
        val shuffleSettling: Boolean = false,
        val detailWordKey: String?,
        /** 详情页选图后打开的图片编辑器（P3-A07） */
        val editorWordKey: String? = null,
        /** 编辑器「换图」时弹出的选图 Sheet */
        val imagePickSheetWordKey: String? = null,
        val showGuide: Boolean,
        val allFlippedToBack: Boolean,
        /** 兼容旧状态构造；详情页实际按卡片 sourceMaterials 分区展示。 */
        val dictionaries: List<com.wordflip.core.model.book.DictionaryItem> = emptyList(),
    ) : StudyUiState

    data class Error(val message: String) : StudyUiState
}

/** 一次性 UI 事件：Toast / TTS 等 */
sealed interface StudyUiEvent {
    data class Toast(val message: String) : StudyUiEvent
}
