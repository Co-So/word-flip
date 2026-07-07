package com.wordflip.dto.book;

import com.wordflip.domain.Book;
import com.wordflip.domain.BookSource;

import java.util.List;

/**
 * 词书列表响应（GET /books）。
 */
public class BookListResponse {

    private List<BookItem> books;

    public BookListResponse(List<BookItem> books) {
        this.books = books;
    }

    public List<BookItem> getBooks() {
        return books;
    }

    public record BookItem(
            long id,
            String name,
            BookSource source,
            int wordCount,
            Integer declaredCount,
            boolean selected,
            boolean canDelete
    ) {
        public static BookItem from(Book book, boolean selected) {
            return new BookItem(
                    book.getId(),
                    book.getName(),
                    book.getSource(),
                    book.getWordCount(),
                    book.getDeclaredCount(),
                    selected,
                    book.getSource() == BookSource.imported
            );
        }
    }
}
