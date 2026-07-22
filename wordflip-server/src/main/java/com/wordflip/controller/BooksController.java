package com.wordflip.controller;

import com.wordflip.dto.book.BookImportConfirmRequest;
import com.wordflip.dto.book.BookImportConfirmResponse;
import com.wordflip.dto.book.BookImportPreviewResponse;
import com.wordflip.dto.book.BookListResponse;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.BookImportService;
import com.wordflip.service.BookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 词书 API：列表、详情、导入 preview+confirm 与删除私有词书。
 */
@RestController
@RequestMapping("/api/v1/books")
public class BooksController {

    private final BookService bookService;
    private final BookImportService bookImportService;

    public BooksController(BookService bookService, BookImportService bookImportService) {
        this.bookService = bookService;
        this.bookImportService = bookImportService;
    }

    @GetMapping
    public BookListResponse listBooks() {
        return bookService.listBooks(SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/{bookId}")
    public BookListResponse.BookItem getBook(@PathVariable Long bookId) {
        return bookService.getBook(SecurityUtils.getCurrentUserId(), bookId);
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BookImportPreviewResponse previewImport(@RequestPart("file") MultipartFile file) {
        return bookImportService.preview(SecurityUtils.getCurrentUserId(), file);
    }

    @PostMapping("/import")
    public ResponseEntity<BookImportConfirmResponse> confirmImport(
            @Valid @RequestBody BookImportConfirmRequest request
    ) {
        BookImportConfirmResponse body = bookImportService.confirm(
                SecurityUtils.getCurrentUserId(),
                request.previewToken(),
                request.name()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @DeleteMapping("/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBook(@PathVariable Long bookId) {
        bookImportService.deleteImportedBook(SecurityUtils.getCurrentUserId(), bookId);
    }
}
