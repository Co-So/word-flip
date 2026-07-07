package com.wordflip.controller;

import com.wordflip.dto.book.BookListResponse;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.BookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 词书列表：GET /books（builtin + 当前用户 imported，含 selected）。
 */
@RestController
@RequestMapping("/api/v1/books")
public class BooksController {

    private final BookService bookService;

    public BooksController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public BookListResponse listBooks() {
        return bookService.listBooks(SecurityUtils.getCurrentUserId());
    }
}
