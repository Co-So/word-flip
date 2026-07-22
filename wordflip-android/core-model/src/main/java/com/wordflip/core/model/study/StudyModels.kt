package com.wordflip.core.model.study

import com.google.gson.annotations.SerializedName
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.model.media.StainConfig

/**
 * 掌握度三态，对齐 openapi `MasteryLevel`；队列/薄弱用，组详情主展示为热力。
 */
enum class MasteryLevel {
    UNLEARNED,
    FUZZY,
    UNKNOWN,
}

/** 掌握度快照，对齐 openapi `MasterySnapshot`（含稳定性热力；按 skill 双轨） */
data class MasterySnapshot(
    val level: MasteryLevel,
    val hasQuizHistory: Boolean,
    val stage: Int? = null,
    val nextReviewAt: String? = null,
    val stability: Double = 0.0,
    val heatLevel: Int = 0,
    /** 技能轨：dictation / choice；缺省按默写兼容旧响应 */
    val skill: String? = "dictation",
)

/**
 * 双 skill 进度 + 展示热力，对齐 openapi `WordProgressSnapshot`。
 * 组详情主展示用 [displayHeatLevel]；薄弱角标看各 skill 的 [MasterySnapshot.level]。
 */
data class WordProgressSnapshot(
    val dictation: MasterySnapshot,
    val choice: MasterySnapshot,
    val displayHeatLevel: Int,
    val displayStability: Double,
    val heatDisplayMode: String = "combined",
)

/** 单词摘要字段，对齐 openapi `WordSummary`；顶层 cn/enGloss/pos/ph = 展示义 */
data class WordSummary(
    val wordKey: String,
    val en: String,
    val cn: String? = null,
    val pos: String? = null,
    val ph: String? = null,
    val enGloss: String? = null,
    /** 全部义项（详情用）；缺省时空列表，客户端退化为顶层展示义 */
    val senses: List<Sense> = emptyList(),
    val cardId: Long = 0,
    val lexemeId: Long = 0,
    val bookId: Long = 0,
    val version: Int = 1,
) {
    /** 卡片/详情主释义：中文优先，否则英英 gloss（Gson 可能把 cn 反成 null） */
    fun displayMeaning(): String =
        preferredMeaning(cn, enGloss, senses)
}

/** 详情抽屉内容（过渡期兼容；优先用 [WordCard.senses]） */
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
 * 学习页网格不展示 mastery Chip（REQ-STUDY-24）；背面 = primary.cn。
 */
data class WordCard(
    val wordKey: String,
    val en: String,
    val cn: String? = null,
    val pos: String? = null,
    @SerializedName("phonetic") val ph: String? = null,
    val enGloss: String? = null,
    val senses: List<Sense> = emptyList(),
    val detail: WordDetail? = null,
    val image: WordImagePayload = WordImagePayload(),
    val stain: WordStainPayload = WordStainPayload(),
    val cardId: Long = 0,
    val lexemeId: Long = 0,
    val bookId: Long = 0,
    val version: Int = 1,
    val progress: CardProgress? = null,
    val sourceMaterials: List<SourceMaterial> = emptyList(),
) {
    /** 卡片背面/详情主释义：中文优先，否则英英 gloss（Gson 可能把 cn 反成 null） */
    fun displayMeaning(): String =
        preferredMeaning(cn, enGloss, senses)

    /**
     * 详情用义项：有 senses 则按 sortOrder；否则用 detail/顶层释义合成单义。
     */
    fun sensesForDetail(): List<Sense> {
        if (senses.isNotEmpty()) {
            return senses.sortedBy { it.sortOrder }
        }
        val meaning = detail?.meaning?.takeIf { it.isNotBlank() } ?: displayMeaning()
        if (meaning.isBlank()) {
            return emptyList()
        }
        val flatExamples = detail?.examples.orEmpty().mapIndexed { i, text ->
            Example(en = text, cn = null, sortOrder = i)
        }
        return listOf(
            Sense(
                pos = pos,
                cn = meaning.takeIf { it.any { ch -> ch in '\u4e00'..'\u9fff' } },
                enGloss = meaning.takeUnless { it.any { ch -> ch in '\u4e00'..'\u9fff' } },
                primary = true,
                quality = "ok",
                sortOrder = 0,
                examples = flatExamples,
            ),
        )
    }
}

data class StudyGroupInfo(
    val id: Int,
    val name: String,
    val source: String = "auto",
)

/** 学习页载荷，对齐 openapi `StudyGroupPayload` */
data class StudyGroupPayload(
    val group: StudyGroupInfo,
    @SerializedName("cards") val words: List<WordCard>,
)

/** 服务端权威 FSRS 状态；Android 仅展示，不计算间隔或评分。 */
data class FsrsMemory(
    val state: String,
    val dueAt: String,
    val stability: Double,
    val difficulty: Double,
    val reps: Int,
    val lapses: Int,
)

/** 默写和选择题两条互不覆盖的记忆轨。 */
data class CardProgress(
    val dictation: FsrsMemory,
    val choice: FsrsMemory,
    /** 服务端计算的只读热力档；Android 不复制稳定性阈值。 */
    val displayHeatLevel: Int,
)

/** 详情抽屉中的词典来源资料。 */
data class SourceMaterial(
    val sourceId: String,
    val sourceName: String,
    val revision: String,
    val licenseNote: String? = null,
    val rawEntryId: Long,
    val senses: List<Sense> = emptyList(),
)

/** 扁平兼容字段缺失时，从词书主考义读取展示文本。 */
private fun preferredMeaning(
    cn: String?,
    enGloss: String?,
    senses: List<Sense>,
): String {
    cn?.takeIf { it.isNotBlank() }?.let { return it }
    enGloss?.takeIf { it.isNotBlank() }?.let { return it }
    return senses.asSequence()
        .sortedBy { it.sortOrder }
        .firstOrNull { it.primary && it.displayMeaning().isNotBlank() }
        ?.displayMeaning()
        ?: senses.asSequence()
            .sortedBy { it.sortOrder }
            .map { it.displayMeaning() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
}
