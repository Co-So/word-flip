package com.wordflip.core.network.word

import com.wordflip.core.model.study.Sense
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.WordLookupResponse
import com.wordflip.core.network.api.WordsApi

/**
 * 单词释义查询 Repository：详情抽屉内临时切换词典。
 */
class WordLookupRepository(
    private val wordsApi: WordsApi,
    private val apiErrorParser: ApiErrorParser,
) {

    /**
     * 按指定词典查询单词释义。
     *
     * @param wordKey 单词键
     * @param dictId  词典 ID，null 时用用户 activeDictId
     * @return 该词典下的释义（含全部义项）
     */
    suspend fun lookupWord(wordKey: String, dictId: String? = null): Result<WordLookupResult> = apiCall {
        val response = wordsApi.lookupWord(wordKey, dictId)
        WordLookupResult(
            wordKey = response.wordKey,
            en = response.en,
            cn = response.cn,
            pos = response.pos,
            ph = response.ph,
            enGloss = response.enGloss,
            senses = response.senses,
            dictId = response.dictId,
            dictName = response.dictName,
            dictLocale = response.dictLocale,
        )
    }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (throwable: Throwable) {
        Result.failure(Exception(apiErrorParser.parseMessage(throwable), throwable))
    }
}

/**
 * 单词释义查询结果，供详情抽屉展示。
 */
data class WordLookupResult(
    val wordKey: String,
    val en: String,
    val cn: String? = null,
    val pos: String? = null,
    val ph: String? = null,
    val enGloss: String? = null,
    val senses: List<Sense> = emptyList(),
    val dictId: String,
    val dictName: String,
    val dictLocale: String,
) {
    /** 展示释义：中文优先，否则英英 gloss */
    fun displayMeaning(): String =
        cn?.takeIf { it.isNotBlank() } ?: enGloss?.takeIf { it.isNotBlank() }.orEmpty()
}
