package com.wordflip.controller;

import com.wordflip.dto.group.CreateCustomGroupRequest;
import com.wordflip.dto.group.GroupCardsResponse;
import com.wordflip.dto.group.GroupDetail;
import com.wordflip.dto.group.GroupListResponse;
import com.wordflip.dto.group.UnassignedCardsResponse;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前学习计划的卡片分组 API。
 */
@RestController
@RequestMapping("/api/v1")
public class GroupController {

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @GetMapping("/groups")
    public GroupListResponse listGroups(
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "createdAt") String sort
    ) {
        return service.listGroups(SecurityUtils.getCurrentUserId(), source, sort);
    }

    @GetMapping("/groups/{groupId}")
    public GroupDetail getGroup(@PathVariable Long groupId) {
        return service.getGroup(SecurityUtils.getCurrentUserId(), groupId);
    }

    @GetMapping("/groups/{groupId}/cards")
    public GroupCardsResponse listGroupCards(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listGroupCards(SecurityUtils.getCurrentUserId(), groupId, page, size);
    }

    @PostMapping("/groups/custom")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDetail createCustomGroup(@Valid @RequestBody CreateCustomGroupRequest request) {
        return service.createCustomGroup(SecurityUtils.getCurrentUserId(), request);
    }

    @GetMapping("/learning/cards/unassigned")
    public UnassignedCardsResponse listUnassignedCards(
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listUnassignedCards(SecurityUtils.getCurrentUserId(), all, q, page, size);
    }
}
