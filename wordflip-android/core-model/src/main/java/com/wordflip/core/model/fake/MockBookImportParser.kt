package com.wordflip.core.model.fake

import com.wordflip.core.model.book.BookImportParseException
import com.wordflip.core.model.book.ParsedBookImport
import com.wordflip.core.model.study.WordSummary

/**
 * 本地词书文件解析（Mock 阶段替代 POST /books/import/preview 服务端解析）。
 * 支持 TXT/CSV/JSON 简化格式（REQ-BOOK-6）。
 */
object MockBookImportParser {

    /**
     * @param content 文件全文
     * @param fileName 用于推断格式与 suggestedName
     */
    fun parse(content: String, fileName: String): ParsedBookImport {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            throw BookImportParseException("未识别到有效单词")
        }
        val suggestedName = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.')
            .ifBlank { "导入词书" }

        val rawLines = when {
            trimmed.startsWith("[") -> parseJsonArray(trimmed)
            else -> parseDelimitedLines(trimmed)
        }
        if (rawLines.isEmpty()) {
            throw BookImportParseException("未识别到有效单词")
        }

        val seen = mutableSetOf<String>()
        val words = mutableListOf<WordSummary>()
        var deduplicated = 0
        rawLines.forEach { (en, cn) ->
            val key = en.trim().lowercase()
            if (key.isBlank() || cn.isBlank()) return@forEach
            if (!seen.add(key)) {
                deduplicated++
                return@forEach
            }
            words.add(
                WordSummary(
                    wordKey = key,
                    en = en.trim(),
                    cn = cn.trim(),
                ),
            )
        }
        if (words.isEmpty()) {
            throw BookImportParseException("未识别到有效单词")
        }
        return ParsedBookImport(
            suggestedName = suggestedName,
            words = words,
            deduplicatedCount = deduplicated,
        )
    }

    /** TXT/CSV：每行 英文,中文 或 英文\t中文；# 开头为注释 */
    private fun parseDelimitedLines(content: String): List<Pair<String, String>> {
        return content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = when {
                    '\t' in line -> line.split('\t', limit = 2)
                    ',' in line -> line.split(',', limit = 2)
                    else -> null
                } ?: return@mapNotNull null
                if (parts.size < 2) return@mapNotNull null
                parts[0] to parts[1]
            }
            .toList()
    }

    /** JSON：[{ "en": "...", "cn": "..." }] 简化数组 */
    private fun parseJsonArray(content: String): List<Pair<String, String>> {
        val objectPattern = Regex("""\{\s*"en"\s*:\s*"([^"]*)"\s*,\s*"cn"\s*:\s*"([^"]*)"\s*\}""")
        return objectPattern.findAll(content)
            .map { match -> match.groupValues[1] to match.groupValues[2] }
            .toList()
    }
}
