package com.wordflip.controller;

import com.wordflip.dto.learning.BookCardsResponse;
import com.wordflip.dto.learning.LearningCardDetailResponse;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.LearningCardQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 词书已发布学习卡与学习卡详情 API。
 */
@RestController
@RequestMapping("/api/v1")
public class LearningCardController {

    private final LearningCardQueryService service;

    public LearningCardController(LearningCardQueryService service) {
        this.service = service;
    }

    @GetMapping("/books/{bookId}/cards")
    public BookCardsResponse listBookCards(
            @PathVariable Long bookId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listBookCards(SecurityUtils.getCurrentUserId(), bookId, page, size);
    }

    @GetMapping("/learning/cards/{cardId}")
    public LearningCardDetailResponse getCard(@PathVariable Long cardId) {
        return service.getCurrentCard(SecurityUtils.getCurrentUserId(), cardId);
    }
}
