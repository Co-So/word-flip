package com.wordflip.controller;

import com.wordflip.dto.quiz.AnswerResultResponse;
import com.wordflip.dto.quiz.CreateQuizSessionRequest;
import com.wordflip.dto.quiz.QuizResultResponse;
import com.wordflip.dto.quiz.QuizSessionCreatedResponse;
import com.wordflip.dto.quiz.SubmitAnswerRequest;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.QuizService;
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
 * 默写测验 API：POST /quiz/sessions、answer、GET result（P2-B08）。
 */
@RestController
@RequestMapping("/api/v1")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/quiz/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizSessionCreatedResponse createSession(
            @RequestBody CreateQuizSessionRequest request,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        ZoneId zoneId = UserTimeZoneUtil.resolveZone(timezone);
        return quizService.createSession(SecurityUtils.getCurrentUserId(), request, zoneId);
    }

    @PostMapping("/quiz/sessions/{sessionId}/answer")
    public AnswerResultResponse submitAnswer(
            @PathVariable String sessionId,
            @Valid @RequestBody SubmitAnswerRequest request,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        ZoneId zoneId = UserTimeZoneUtil.resolveZone(timezone);
        return quizService.submitAnswer(SecurityUtils.getCurrentUserId(), sessionId, request, zoneId);
    }

    @GetMapping("/quiz/sessions/{sessionId}/result")
    public QuizResultResponse getResult(@PathVariable String sessionId) {
        return quizService.getResult(SecurityUtils.getCurrentUserId(), sessionId);
    }
}
