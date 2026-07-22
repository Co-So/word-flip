package com.wordflip.controller;

import com.wordflip.dto.learning.CurrentWordResponse;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.LearningCardQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询当前主词书学习卡及可展开来源资料，不接受全局词典参数。
 */
@RestController
@RequestMapping("/api/v1")
public class WordLookupController {

    private final LearningCardQueryService service;

    public WordLookupController(LearningCardQueryService service) {
        this.service = service;
    }

    @GetMapping("/words/{wordKey}")
    public CurrentWordResponse lookupWord(@PathVariable String wordKey) {
        return service.lookupCurrentWord(SecurityUtils.getCurrentUserId(), wordKey);
    }
}
