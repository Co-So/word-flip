package com.wordflip.controller;

import com.wordflip.dto.word.WordLookupResponse;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.WordLookupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单词查询 API：按词典查释义（REQ-LEX-9 详情抽屉临时切换）。
 * <p>
 * GET /api/v1/words/{wordKey}?dictId=xxx：返回指定词典下的完整释义；
 * 缺省 dictId 时使用用户当前 activeDictId。
 * 不影响用户全局设置，仅用于浏览。
 */
@RestController
@RequestMapping("/api/v1")
public class WordLookupController {

    private final WordLookupService wordLookupService;

    public WordLookupController(WordLookupService wordLookupService) {
        this.wordLookupService = wordLookupService;
    }

    /**
     * 按词典查询单词释义。
     *
     * @param wordKey 单词键（en.trim().toLowerCase()）
     * @param dictId  词典 ID，可选；缺省时用用户 activeDictId
     * @return 该词典下的完整释义（含全部义项）
     */
    @GetMapping("/words/{wordKey}")
    public WordLookupResponse lookupWord(
            @PathVariable String wordKey,
            @RequestParam(required = false) String dictId
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        return wordLookupService.lookupWord(userId, wordKey, dictId);
    }
}
