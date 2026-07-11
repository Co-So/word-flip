package com.wordflip.controller;

import com.wordflip.dto.dict.DictionaryItemDto;
import com.wordflip.repository.DictionaryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/v1/dicts：列出内置词典（REQ-LEX-9）。
 */
@RestController
@RequestMapping("/api/v1/dicts")
public class DictsController {

    private final DictionaryRepository dictionaryRepository;

    public DictsController(DictionaryRepository dictionaryRepository) {
        this.dictionaryRepository = dictionaryRepository;
    }

    @GetMapping
    public List<DictionaryItemDto> listDictionaries() {
        return dictionaryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(DictionaryItemDto::from)
                .toList();
    }
}
