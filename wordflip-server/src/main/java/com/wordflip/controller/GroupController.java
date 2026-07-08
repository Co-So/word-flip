package com.wordflip.controller;

import com.wordflip.domain.GroupSource;
import com.wordflip.dto.group.CreateCustomGroupRequest;
import com.wordflip.dto.group.GroupDetail;
import com.wordflip.dto.group.GroupListResponse;
import com.wordflip.dto.group.GroupWordsResponse;
import com.wordflip.dto.word.UnassignedWordsResponse;
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
 * 分组读/写 API：GET /groups、GET /groups/{id}、GET /groups/{id}/words、POST /groups/custom。
 */
@RestController
@RequestMapping("/api/v1")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/groups")
    public GroupListResponse listGroups(
            @RequestParam(required = false) GroupSource source,
            @RequestParam(defaultValue = "createdAt") String sort
    ) {
        return groupService.listGroups(SecurityUtils.getCurrentUserId(), source, sort);
    }

    @GetMapping("/groups/{groupId}")
    public GroupDetail getGroup(@PathVariable Long groupId) {
        return groupService.getGroup(SecurityUtils.getCurrentUserId(), groupId);
    }

    @GetMapping("/groups/{groupId}/words")
    public GroupWordsResponse listGroupWords(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return groupService.listGroupWords(SecurityUtils.getCurrentUserId(), groupId, page, size);
    }

    @PostMapping("/groups/custom")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDetail createCustomGroup(@Valid @RequestBody CreateCustomGroupRequest request) {
        return groupService.createCustomGroup(SecurityUtils.getCurrentUserId(), request);
    }

    @GetMapping("/words/unassigned")
    public UnassignedWordsResponse listUnassignedWords(
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return groupService.listUnassignedWords(SecurityUtils.getCurrentUserId(), all, q, page, size);
    }
}
