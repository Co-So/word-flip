package com.wordflip.controller;

import com.wordflip.dto.stain.StainBatchRequest;
import com.wordflip.dto.stain.StainBatchResponse;
import com.wordflip.dto.stain.StainUpdateRequest;
import com.wordflip.dto.stain.WordStainResponse;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.StainService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 污渍 API：污渍按用户与学习卡隔离。
 * <p>
 * 主要错误码：VALIDATION_ERROR、NOT_FOUND（分组不存在）。
 */
@RestController
@RequestMapping("/api/v1")
public class StainController {

    private final StainService stainService;

    public StainController(StainService stainService) {
        this.stainService = stainService;
    }

    /**
     * GET /learning/cards/{cardId}/stain：无行时返回默认 seed，不落库。
     */
    @GetMapping("/learning/cards/{cardId}/stain")
    public WordStainResponse getStain(@PathVariable Long cardId) {
        return stainService.getStain(SecurityUtils.getCurrentUserId(), cardId);
    }

    /**
     * PUT /learning/cards/{cardId}/stain：regenerate / set_hidden / set_visible / replace。
     */
    @PutMapping("/learning/cards/{cardId}/stain")
    public WordStainResponse updateStain(
            @PathVariable Long cardId,
            @Valid @RequestBody StainUpdateRequest request
    ) {
        return stainService.updateStain(SecurityUtils.getCurrentUserId(), cardId, request);
    }

    /**
     * POST /groups/{groupId}/stains/batch — 一键为组内词 regenerate。
     */
    @PostMapping("/groups/{groupId}/stains/batch")
    public StainBatchResponse batchRegenerate(
            @PathVariable Long groupId,
            @RequestBody(required = false) StainBatchRequest request
    ) {
        StainBatchRequest body = request != null ? request : new StainBatchRequest();
        return stainService.batchRegenerate(SecurityUtils.getCurrentUserId(), groupId, body);
    }
}
