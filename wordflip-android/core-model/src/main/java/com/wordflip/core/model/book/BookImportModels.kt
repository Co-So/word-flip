package com.wordflip.core.model.book

import com.wordflip.core.model.study.WordSummary

/** 导入预览响应，对齐 openapi `BookImportPreviewResponse` */
data class BookImportPreviewResponse(
    val previewToken: String,
    val suggestedName: String,
    val totalCount: Int,
    val deduplicatedCount: Int? = null,
    val previewWords: List<WordSummary>,
)

/** 导入确认请求，对齐 openapi `BookImportConfirmRequest` */
data class BookImportConfirmRequest(
    val previewToken: String,
    val name: String,
)

/** 导入确认响应，对齐 openapi `BookImportConfirmResponse` */
data class BookImportConfirmResponse(
    val book: BookItem,
)

/** 本地解析结果，供 Mock preview 暂存 */
data class ParsedBookImport(
    val suggestedName: String,
    val words: List<WordSummary>,
    val deduplicatedCount: Int,
)

/** 词书解析失败（REQ-BOOK-7） */
class BookImportParseException(message: String) : Exception(message)
