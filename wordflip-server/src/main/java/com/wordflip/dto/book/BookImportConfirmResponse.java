package com.wordflip.dto.book;

/**
 * 确认导入响应（POST /books/import）。
 */
public record BookImportConfirmResponse(BookListResponse.BookItem book) {
}
