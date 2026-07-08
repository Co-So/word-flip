package com.wordflip.controller;

import com.wordflip.dto.study.StudyGroupPayload;
import com.wordflip.dto.study.StudySessionReportRequest;
import com.wordflip.dto.study.StudySessionReportResponse;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.StudyService;
import com.wordflip.util.UserTimeZoneUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;

/**
 * 学习页 API：GET /study/groups/{groupId}、POST /study/sessions（P1-B10~B12）。
 */
@RestController
@RequestMapping("/api/v1")
public class StudyController {

    private final StudyService studyService;

    public StudyController(StudyService studyService) {
        this.studyService = studyService;
    }

    @GetMapping("/study/groups/{groupId}")
    public StudyGroupPayload getStudyGroup(@PathVariable Long groupId) {
        return studyService.getStudyGroup(SecurityUtils.getCurrentUserId(), groupId);
    }

    @PostMapping("/study/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public StudySessionReportResponse reportSession(
            @Valid @RequestBody StudySessionReportRequest request,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        ZoneId zoneId = UserTimeZoneUtil.resolveZone(timezone);
        return studyService.reportSession(SecurityUtils.getCurrentUserId(), request, zoneId);
    }
}
