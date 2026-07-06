package com.wordflip.core.model.study

import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.model.media.StainConfig

/**
 * 掌握度三态，对齐 openapi `MasteryLevel`；仅测验写入，学习页只读展示（分组详情用）。
 */
enum class MasteryLevel {
    UNLEARNED,
    FUZZY,
    UNKNOWN,
}

/** 掌握度快照，对齐 openapi `MasterySnapshot` */
data class MasterySnapshot(
    val level: MasteryLevel,
    val hasQuizHistory: Boolean,
    val stage: Int? = null,
    val nextReviewAt: String? = null,
)

/** 单词摘要字段，对齐 openapi `WordSummary` */
data class WordSummary(
    val wordKey: String,
    val en: String,
    val cn: String,
    val pos: String? = null,
    val ph: String? = null,
)

/** 详情抽屉内容 */
data class WordDetail(
    val meaning: String,
    val examples: List<String> = emptyList(),
    val etymology: String? = null,
)

data class WordImagePayload(
    val hasImage: Boolean = false,
    val imageUrl: String? = null,
    /** 有图时是否在底部 overlay 显示中文（REQ-STUDY-19） */
    val showCnOnImage: Boolean = true,
    val transform: ImageTransform? = null,
    val filters: ImageFilters? = null,
)

/** 污渍配置；seed/config 供 Canvas 确定性渲染（REQ-STAIN-1） */
data class WordStainPayload(
    val hidden: Boolean = false,
    val seed: Long = 0L,
    val config: StainConfig? = null,
)

/**
 * 学习页单词卡片，对齐 openapi `WordCard`。
 * 学习页网格不展示 mastery Chip（REQ-STUDY-24）。
 */
data class WordCard(
    val wordKey: String,
    val en: String,
    val cn: String,
    val pos: String? = null,
    val ph: String? = null,
    val mastery: MasterySnapshot,
    val detail: WordDetail? = null,
    val image: WordImagePayload = WordImagePayload(),
    val stain: WordStainPayload = WordStainPayload(),
)

data class StudyGroupInfo(
    val id: Int,
    val name: String,
    val source: String = "auto",
)

/** 学习页载荷，对齐 openapi `StudyGroupPayload` */
data class StudyGroupPayload(
    val group: StudyGroupInfo,
    val words: List<WordCard>,
)
