package com.wordflip.service;

import com.wordflip.domain.GroupWord;
import com.wordflip.domain.QuizAnswer;
import com.wordflip.domain.QuizQuestion;
import com.wordflip.domain.QuizSession;
import com.wordflip.domain.QuizSessionSource;
import com.wordflip.domain.QuizSessionStatus;
import com.wordflip.domain.StudyGroup;
import com.wordflip.dto.quiz.AnswerResultResponse;
import com.wordflip.dto.quiz.CreateQuizSessionRequest;
import com.wordflip.dto.quiz.MasteryUpdateDto;
import com.wordflip.dto.quiz.QuizPromptDto;
import com.wordflip.dto.quiz.QuizQuestionDto;
import com.wordflip.dto.quiz.QuizResultResponse;
import com.wordflip.dto.quiz.QuizSessionCreatedResponse;
import com.wordflip.dto.quiz.QuizSessionProgressDto;
import com.wordflip.dto.quiz.QuizWrongWordDto;
import com.wordflip.dto.quiz.SubmitAnswerRequest;
import com.wordflip.dto.word.MasterySnapshot;
import com.wordflip.dto.word.WordSummary;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.GroupRepository;
import com.wordflip.repository.GroupWordRepository;
import com.wordflip.repository.QuizAnswerRepository;
import com.wordflip.repository.QuizQuestionRepository;
import com.wordflip.repository.QuizSessionRepository;
import com.wordflip.repository.TodayQueryRepository;
import com.wordflip.util.UserTimeZoneUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 默写测验编排：POST /quiz/sessions、answer、result（P2-B06~B08）。
 */
@Service
public class QuizService {

    private final QuizSessionRepository quizSessionRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final GroupRepository groupRepository;
    private final GroupWordRepository groupWordRepository;
    private final TodayQueryRepository todayQueryRepository;
    private final WordLookupService wordLookupService;
    private final ReviewService reviewService;
    private final TodayCacheService todayCacheService;

    public QuizService(
            QuizSessionRepository quizSessionRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizAnswerRepository quizAnswerRepository,
            GroupRepository groupRepository,
            GroupWordRepository groupWordRepository,
            TodayQueryRepository todayQueryRepository,
            WordLookupService wordLookupService,
            ReviewService reviewService,
            TodayCacheService todayCacheService
    ) {
        this.quizSessionRepository = quizSessionRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizAnswerRepository = quizAnswerRepository;
        this.groupRepository = groupRepository;
        this.groupWordRepository = groupWordRepository;
        this.todayQueryRepository = todayQueryRepository;
        this.wordLookupService = wordLookupService;
        this.reviewService = reviewService;
        this.todayCacheService = todayCacheService;
    }

    /** POST /quiz/sessions：抽题并返回首题 */
    @Transactional
    public QuizSessionCreatedResponse createSession(
            Long userId,
            CreateQuizSessionRequest request,
            ZoneId zoneId
    ) {
        QuizSessionSource source = parseSource(request.getSource());
        Long groupId = request.getGroupId();
        int questionLimit = normalizeLimit(request.getQuestionLimit());

        if (source == QuizSessionSource.study) {
            if (groupId == null) {
                throw new WordflipException("VALIDATION_ERROR", "study 源须指定 groupId");
            }
            requireOwnedGroup(userId, groupId);
        }

        List<String> pool = buildPool(userId, source, groupId, zoneId);
        if (pool.isEmpty()) {
            throw new WordflipException("EMPTY_POOL", "无题可出");
        }

        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        int total = Math.min(shuffled.size(), questionLimit);
        List<String> selected = shuffled.subList(0, total);
        Map<String, WordSummary> summaries = wordLookupService.resolveWordSummaries(userId, selected);

        String sessionId = UUID.randomUUID().toString();
        QuizSession session = new QuizSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setSource(source);
        session.setGroupId(groupId);
        session.setQuestionLimit(questionLimit);
        session.setTotalQuestions(total);
        session.setStartedAt(Instant.now());
        quizSessionRepository.save(session);

        List<QuizQuestion> questionEntities = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            String wordKey = selected.get(i);
            WordSummary summary = summaries.getOrDefault(wordKey, fallbackSummary(wordKey));
            QuizQuestion entity = new QuizQuestion();
            entity.setSessionId(sessionId);
            entity.setQuestionIndex(i);
            entity.setWordKey(wordKey);
            entity.setExpectedEn(summary.en());
            entity.setPromptCn(summary.cn());
            entity.setPromptPos(summary.pos());
            entity.setPromptPh(summary.ph());
            questionEntities.add(entity);
        }
        quizQuestionRepository.saveAll(questionEntities);

        QuizQuestionDto firstQuestion = toQuestionDto(questionEntities.getFirst());
        return new QuizSessionCreatedResponse(
                sessionId,
                QuizSessionStatus.in_progress.name(),
                total,
                0,
                0,
                firstQuestion
        );
    }

    /** POST /quiz/sessions/{sessionId}/answer：判题 + applyQuizResult */
    @Transactional
    public AnswerResultResponse submitAnswer(
            Long userId,
            String sessionId,
            SubmitAnswerRequest request,
            ZoneId zoneId
    ) {
        QuizSession session = quizSessionRepository.findByIdAndUserIdAndStatus(
                        sessionId,
                        userId,
                        QuizSessionStatus.in_progress
                )
                .orElseThrow(() -> new WordflipException("CONFLICT", "会话已完成或不存在"));

        int questionIndex = request.getQuestionIndex();
        if (questionIndex != session.getCurrentIndex()) {
            throw new WordflipException("CONFLICT", "题号不匹配");
        }
        if (quizAnswerRepository.existsBySessionIdAndQuestionIndex(sessionId, questionIndex)) {
            throw new WordflipException("CONFLICT", "该题已作答");
        }

        QuizQuestion question = quizQuestionRepository.findBySessionIdAndQuestionIndex(sessionId, questionIndex)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "题目不存在"));

        String trimmed = request.getAnswer().trim();
        boolean correct = trimmed.equalsIgnoreCase(question.getExpectedEn());

        // 连续答错：写入本条前最近一条也为错（跨 session）
        boolean consecutiveWrong = !correct
                && quizAnswerRepository.findFirstByUserIdAndWordKeyOrderByAnsweredAtDescIdDesc(
                        userId,
                        question.getWordKey()
                )
                .map(prev -> !prev.isCorrect())
                .orElse(false);

        MasterySnapshot before = reviewService.buildMasterySnapshot(userId, question.getWordKey());
        reviewService.applyQuizResult(userId, question.getWordKey(), correct, consecutiveWrong, zoneId);
        MasterySnapshot after = reviewService.buildMasterySnapshot(userId, question.getWordKey());

        QuizAnswer answer = new QuizAnswer();
        answer.setUserId(userId);
        answer.setSessionId(sessionId);
        answer.setQuestionId(question.getId());
        answer.setWordKey(question.getWordKey());
        answer.setQuestionIndex(questionIndex);
        answer.setUserAnswer(trimmed);
        answer.setCorrect(correct);
        answer.setConsecutiveWrong(consecutiveWrong);
        answer.setAnsweredAt(Instant.now());
        quizAnswerRepository.save(answer);

        if (correct) {
            session.setScore(session.getScore() + 1);
        }
        session.setCurrentIndex(questionIndex + 1);

        LocalDate today = UserTimeZoneUtil.todayInZone(zoneId);
        boolean completed = session.getCurrentIndex() >= session.getTotalQuestions();
        if (completed) {
            session.setStatus(QuizSessionStatus.completed);
            session.setCompletedAt(Instant.now());
            // Quiz 完成自动 upsert study_logs（api-modules §2.5）
            reviewService.recordStudySession(userId, today, 0, session.getTotalQuestions());
        }
        quizSessionRepository.save(session);
        todayCacheService.invalidate(userId, today);

        QuizQuestionDto nextQuestion = null;
        if (!completed) {
            QuizQuestion next = quizQuestionRepository.findBySessionIdAndQuestionIndex(
                            sessionId,
                            session.getCurrentIndex()
                    )
                    .orElseThrow(() -> new WordflipException("INTERNAL_ERROR", "下一题不存在"));
            nextQuestion = toQuestionDto(next);
        }

        String feedback = correct ? "correct" : "wrong";
        String expectedEn = correct ? null : question.getExpectedEn();
        MasteryUpdateDto masteryUpdate = new MasteryUpdateDto(question.getWordKey(), before, after);
        QuizSessionProgressDto sessionProgress = new QuizSessionProgressDto(
                completed ? QuizSessionStatus.completed.name() : QuizSessionStatus.in_progress.name(),
                session.getScore(),
                session.getCurrentIndex(),
                session.getTotalQuestions(),
                nextQuestion
        );
        return new AnswerResultResponse(correct, expectedEn, feedback, masteryUpdate, sessionProgress);
    }

    /** GET /quiz/sessions/{sessionId}/result */
    @Transactional(readOnly = true)
    public QuizResultResponse getResult(Long userId, String sessionId) {
        QuizSession session = quizSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "会话不存在"));
        if (session.getStatus() != QuizSessionStatus.completed) {
            throw new WordflipException("CONFLICT", "测验尚未完成");
        }

        List<QuizAnswer> answers = quizAnswerRepository.findBySessionIdOrderByQuestionIndexAsc(sessionId);
        List<QuizQuestion> questions = quizQuestionRepository.findBySessionIdOrderByQuestionIndexAsc(sessionId);
        Map<Integer, QuizQuestion> questionByIndex = questions.stream()
                .collect(java.util.stream.Collectors.toMap(QuizQuestion::getQuestionIndex, q -> q));

        List<QuizWrongWordDto> wrongWords = new ArrayList<>();
        for (QuizAnswer answer : answers) {
            if (!answer.isCorrect()) {
                QuizQuestion q = questionByIndex.get(answer.getQuestionIndex());
                wrongWords.add(new QuizWrongWordDto(
                        answer.getWordKey(),
                        q.getExpectedEn(),
                        q.getPromptCn(),
                        answer.getUserAnswer()
                ));
            }
        }

        int total = session.getTotalQuestions();
        int correctCount = session.getScore();
        int wrongCount = total - correctCount;
        float accuracy = total > 0 ? (float) correctCount / total : 0f;
        String rating = resolveRating(accuracy);

        return new QuizResultResponse(
                sessionId,
                session.getScore(),
                total,
                correctCount,
                wrongCount,
                accuracy,
                rating,
                wrongWords
        );
    }

    /**
     * study 源：组内全部 wordKey（学习页默写测全组）；today/retry 用到期+fuzzy/unknown 池。
     */
    private List<String> buildPool(Long userId, QuizSessionSource source, Long groupId, ZoneId zoneId) {
        if (source == QuizSessionSource.study && groupId != null) {
            return groupWordRepository.findByGroupIdOrderBySortOrderAsc(groupId).stream()
                    .map(GroupWord::getWordKey)
                    .toList();
        }
        LocalDate today = UserTimeZoneUtil.todayInZone(zoneId);
        return todayQueryRepository.findQuizPoolWordKeys(userId, today, groupId);
    }

    private StudyGroup requireOwnedGroup(Long userId, Long groupId) {
        return groupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "分组不存在"));
    }

    private static QuizSessionSource parseSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return QuizSessionSource.today;
        }
        try {
            return QuizSessionSource.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException ex) {
            throw new WordflipException("VALIDATION_ERROR", "无效的 source: " + raw);
        }
    }

    private static int normalizeLimit(Integer limit) {
        int value = limit != null ? limit : 10;
        return Math.min(Math.max(value, 1), 50);
    }

    private static WordSummary fallbackSummary(String wordKey) {
        return new WordSummary(wordKey, wordKey, wordKey, null, null);
    }

    private static QuizQuestionDto toQuestionDto(QuizQuestion entity) {
        return new QuizQuestionDto(
                entity.getQuestionIndex(),
                entity.getWordKey(),
                new QuizPromptDto(entity.getPromptCn(), entity.getPromptPos(), entity.getPromptPh())
        );
    }

    /** REQ-QUIZ-9：excellent≥90%, good≥70% */
    private static String resolveRating(float accuracy) {
        if (accuracy >= 0.9f) {
            return "excellent";
        }
        if (accuracy >= 0.7f) {
            return "good";
        }
        return "keep_going";
    }
}
