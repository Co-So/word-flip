package com.wordflip.core.model.study

/**
 * 义项例句，对齐 openapi `Example`。
 */
data class Example(
    val en: String,
    val cn: String? = null,
    val sortOrder: Int = 0,
)

/**
 * 词典义项，对齐 openapi `Sense`。
 * 卡片背面只用 primary；详情抽屉展示全部 senses。
 * 英汉用 [cn]；英英用 [enGloss]（REQ-LEX-10）。
 */
data class Sense(
    val id: Long? = null,
    val pos: String? = null,
    val cn: String? = null,
    val enGloss: String? = null,
    val primary: Boolean = false,
    val quality: String = "ok",
    val sortOrder: Int = 0,
    val examples: List<Example> = emptyList(),
) {
    /** 展示释义：优先中文 */
    fun displayMeaning(): String =
        cn?.takeIf { it.isNotBlank() } ?: enGloss?.takeIf { it.isNotBlank() }.orEmpty()
}
