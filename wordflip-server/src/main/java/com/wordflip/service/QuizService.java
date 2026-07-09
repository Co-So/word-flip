package com.wordflip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.domain.GroupWord;
import com.wordflip.domain.QuestionType;
import com.wordflip.domain.QuizAnswer;
import com.wordflip.domain.QuizLaunchMode;
import com.wordflip.domain.QuizQuestion;
import com.wordflip.domain.QuizSession;
import com.wordflip.domain.QuizSessionSource;
import com.wordflip.domain.QuizSessionStatus;
import com.wordflip.domain.Skill;
import com.wordflip.domain.StudyGroup;
import com.wordflip.domain.UserRecentGroup;
import com.wordflip.domain.UserRecentGroupId;
import com.wordflip.dto.quiz.AnswerResultResponse;
import com.wordflip.dto.quiz.CreateQuizSessionRequest;
import com.wordflip.dto.quiz.MasteryUpdateDto;
import com.wordflip.dto.quiz.QuizOptionDto;
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
import com.wordflip.repository.UserRecentGroupRepository;
import com.wordflip.util.UserTimeZoneUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 多题型测验编排：建会话、判题、结果；掌握度写入口经 {@link ReviewService#applyQuizResult}。
 */
@Service
public class QuizService {

    private static final TypeReference<List<QuizOptionDto>> OPTIONS_TYPE = new TypeReference<>() {
    };

    private final QuizSessionRepository quizSessionRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final GroupRepository groupRepository;
    private final GroupWordRepository groupWordRepository;
    private final TodayQueryRepository todayQueryRepository;
    private final UserRecentGroupRepository userRecentGroupRepository;
    private final WordLookupService wordLookupService;
    private final ReviewService reviewService;
    private final TodayCacheService todayCacheService;
    private final ObjectMapper objectMapper;

    public QuizService(
            QuizSessionRepository quizSessionRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizAnswerRepository quizAnswerRepository,
            GroupRepository groupRepository,
            GroupWordRepository groupWordRepository,
            TodayQueryRepository todayQueryRepository,
            UserRecentGroupRepository userRecentGroupRepository,
            WordLookupService wordLookupService,
            ReviewService reviewService,
            TodayCacheService todayCacheService,
            ObjectMapper objectMapper
    ) {
        this.quizSessionRepository = quizSessionRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizAnswerRepository = quizAnswerRepository;
        this.groupRepository = groupRepository;
        this.groupWordRepository = groupWordRepository;
        this.todayQueryRepository = todayQueryRepository;
        this.userRecentGroupRepository = userRecentGroupRepository;
        this.wordLookupService = wordLookupService;
        this.reviewService = reviewService;
        this.todayCacheService = todayCacheService;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /quiz/sessions：按 source 建池、分配题型、选择题生成干扰项，返回首题。
     */
    @Transactional
    public QuizSessionCreatedResponse createSession(
            Long userId,
            CreateQuizSessionRequest request,
            ZoneId zoneId
    ) {
        QuizSessionSource source = parseSource(request.getSource());
        QuizLaunchMode launchMode = parseLaunchMode(request.getLaunchMode());
        Long groupId = request.getGroupId();
        List<Long> groupIds = normalizeGroupIds(request.getGroupIds(), groupId);
        int questionLimit = normalizeLimit(request.getQuestionLimit());

        // study / recent 须单组；groups 须至少一组
        if (source == QuizSessionSource.study || source == QuizSessionSource.recent) {
            if (groupId == null) {
                throw new WordflipException("VALIDATION_ERROR", source.name() + " 源须指定 groupId");
            }
            requireOwnedGroup(userId, groupId);
        }
        if (source == QuizSessionSource.groups) {
            if (groupIds.isEmpty()) {
                throw new WordflipException("VALIDATION_ERROR", "groups 源须指定 groupIds");
            }
            for (Long id : groupIds) {
                requireOwnedGroup(userId, id);
            }
        }

        List<String> pool = buildPool(userId, source, groupId, groupIds, zoneId);
        if (pool.isEmpty()) {
            throw new WordflipException("EMPTY_POOL", "无题可出");
        }

        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        int total = Math.min(shuffled.size(), questionLimit);
        List<String> selected = new ArrayList<>(shuffled.subList(0, total));

        List<QuestionType> requestedTypes = parseQuestionTypes(request.getQuestionTypes());
        List<QuestionType> assignedTypes = assignQuestionTypes(total, launchMode, requestedTypes);

        Map<String, WordSummary> summaries = wordLookupService.resolveWordSummaries(userId, selected);
        // 干扰项从用户全部已入组词抽取
        List<String> distractorPool = new ArrayList<>(groupWordRepository.findWordKeysByUserId(userId));

        String sessionId = UUID.randomUUID().toString();
        QuizSession session = new QuizSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setSource(source);
        session.setGroupId(groupId);
        session.setGroupIdsJson(toJsonOrNull(groupIds.isEmpty() ? null : groupIds));
        session.setQuestionLimit(questionLimit);
        session.setQuestionTypesJson(toJsonOrNull(assignedTypes.stream().map(Enum::name).distinct().toList()));
        session.setLaunchMode(launchMode.name());
        session.setTotalQuestions(total);
        session.setStartedAt(Instant.now());
        quizSessionRepository.save(session);

        List<QuizQuestion> questionEntities = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            String wordKey = selected.get(i);
            WordSummary summary = summaries.getOrDefault(wordKey, fallbackSummary(wordKey));
            QuestionType type = assignedTypes.get(i);

            QuizQuestion entity = new QuizQuestion();
            entity.setSessionId(sessionId);
            entity.setQuestionIndex(i);
            entity.setWordKey(wordKey);
            entity.setQuestionType(type);
            entity.setExpectedEn(summary.en());
            entity.setPromptCn(summary.cn() != null ? summary.cn() : "");
            entity.setPromptPos(summary.pos());
            entity.setPromptPh(summary.ph());

            // 选择题：同用户词库抽 3 个干扰项，correctKey 用正确 wordKey
            if (type != QuestionType.dictation) {
                List<QuizOptionDto> options = buildChoiceOptions(userId, wordKey, type, summary, distractorPool, summaries);
                entity.setOptionsJson(toJsonOrNull(options));
                entity.setCorrectKey(wordKey);
            }

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

    /**
     * POST /quiz/sessions/{sessionId}/answer：按题型判题，按同 skill 算连续错，写 applyQuizResult。
     */
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

        QuestionType questionType = question.getQuestionType() != null
                ? question.getQuestionType()
                : QuestionType.dictation;
        Skill skill = questionType.toSkill();

        boolean correct;
        String userAnswerStored;
        if (questionType == QuestionType.dictation) {
            // 默写：trim + equalsIgnoreCase 对 expectedEn
            String trimmed = request.getAnswer() != null ? request.getAnswer().trim() : "";
            correct = trimmed.equalsIgnoreCase(question.getExpectedEn());
            userAnswerStored = trimmed;
        } else {
            // 选择：selectedKey 与 correctKey 精确相等
            String selectedKey = request.getSelectedKey() != null ? request.getSelectedKey().trim() : "";
            String correctKey = question.getCorrectKey() != null ? question.getCorrectKey() : question.getWordKey();
            correct = selectedKey.equals(correctKey);
            userAnswerStored = selectedKey;
        }

        // 连续答错：同 skill 下写入本条前最近一条也为错（跨 session）
        boolean consecutiveWrong = !correct
                && quizAnswerRepository.findFirstByUserIdAndWordKeyAndSkillOrderByAnsweredAtDescIdDesc(
                        userId,
                        question.getWordKey(),
                        skill
                )
                .map(prev -> !prev.isCorrect())
                .orElse(false);

        MasterySnapshot before = reviewService.buildMasterySnapshot(userId, question.getWordKey(), skill);
        reviewService.applyQuizResult(userId, question.getWordKey(), skill, correct, consecutiveWrong, zoneId);
        MasterySnapshot after = reviewService.buildMasterySnapshot(userId, question.getWordKey(), skill);

        QuizAnswer answer = new QuizAnswer();
        answer.setUserId(userId);
        answer.setSessionId(sessionId);
        answer.setQuestionId(question.getId());
        answer.setWordKey(question.getWordKey());
        answer.setSkill(skill);
        answer.setQuestionType(questionType);
        answer.setQuestionIndex(questionIndex);
        answer.setUserAnswer(userAnswerStored);
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
            // 有关联分组时记录最近组（今日页 recentGroups）
            recordRecentGroups(userId, session);
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
                .collect(Collectors.toMap(QuizQuestion::getQuestionIndex, q -> q));

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
     * 按 source 建出题池：study/recent 单组；groups 多组并集去重；all 全已入组；today/retry 到期∪薄弱。
     */
    private List<String> buildPool(
            Long userId,
            QuizSessionSource source,
            Long groupId,
            List<Long> groupIds,
            ZoneId zoneId
    ) {
        return switch (source) {
            case study, recent -> groupWordRepository.findByGroupIdOrderBySortOrderAsc(groupId).stream()
                    .map(GroupWord::getWordKey)
                    .toList();
            case groups -> {
                LinkedHashSet<String> union = new LinkedHashSet<>();
                for (Long id : groupIds) {
                    for (GroupWord gw : groupWordRepository.findByGroupIdOrderBySortOrderAsc(id)) {
                        union.add(gw.getWordKey());
                    }
                }
                yield new ArrayList<>(union);
            }
            case all -> new ArrayList<>(groupWordRepository.findWordKeysByUserId(userId));
            case today, retry -> {
                LocalDate today = UserTimeZoneUtil.todayInZone(zoneId);
                yield todayQueryRepository.findQuizPoolWordKeys(userId, today, groupId);
            }
        };
    }

    /**
     * 分配每题题型：mixed 或 types 空时随机 dictation/choice_*，保证两 skill 各至少 1（题数=1 则 dictation）；
     * free_select 则从请求题型中抽取。
     */
    private List<QuestionType> assignQuestionTypes(
            int total,
            QuizLaunchMode launchMode,
            List<QuestionType> requestedTypes
    ) {
        if (total <= 0) {
            return List.of();
        }
        boolean useFreeSelect = launchMode == QuizLaunchMode.free_select && !requestedTypes.isEmpty();
        if (useFreeSelect) {
            List<QuestionType> assigned = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                assigned.add(requestedTypes.get(ThreadLocalRandom.current().nextInt(requestedTypes.size())));
            }
            return assigned;
        }

        // mixed / types 空：题数=1 默认 dictation
        if (total == 1) {
            return List.of(QuestionType.dictation);
        }

        List<QuestionType> assigned = new ArrayList<>(total);
        assigned.add(QuestionType.dictation);
        assigned.add(randomChoiceType());
        for (int i = 2; i < total; i++) {
            assigned.add(randomAnyType());
        }
        Collections.shuffle(assigned);
        return assigned;
    }

    /** 选择题选项：正确项 + 最多 3 个干扰项，打乱顺序 */
    private List<QuizOptionDto> buildChoiceOptions(
            Long userId,
            String correctWordKey,
            QuestionType type,
            WordSummary correctSummary,
            List<String> distractorPool,
            Map<String, WordSummary> knownSummaries
    ) {
        List<String> candidates = distractorPool.stream()
                .filter(k -> !k.equals(correctWordKey))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates);
        List<String> distractors = candidates.stream().limit(3).toList();

        List<String> needLookup = distractors.stream()
                .filter(k -> !knownSummaries.containsKey(k))
                .toList();
        if (!needLookup.isEmpty()) {
            knownSummaries.putAll(wordLookupService.resolveWordSummaries(userId, needLookup));
        }

        List<QuizOptionDto> options = new ArrayList<>();
        options.add(toOption(correctWordKey, correctSummary, type));
        for (String key : distractors) {
            WordSummary summary = knownSummaries.getOrDefault(key, fallbackSummary(key));
            options.add(toOption(key, summary, type));
        }
        Collections.shuffle(options);
        return options;
    }

    private static QuizOptionDto toOption(String wordKey, WordSummary summary, QuestionType type) {
        // choice_en_cn：选项为中文；choice_cn_en：选项为英文；key 统一用 wordKey
        String label = type == QuestionType.choice_en_cn
                ? (summary.cn() != null && !summary.cn().isBlank() ? summary.cn() : wordKey)
                : (summary.en() != null && !summary.en().isBlank() ? summary.en() : wordKey);
        return new QuizOptionDto(wordKey, label);
    }

    /** 会话完成时 upsert 最近学习组（groupId 或 groupIds） */
    private void recordRecentGroups(Long userId, QuizSession session) {
        Set<Long> ids = new LinkedHashSet<>();
        if (session.getGroupId() != null) {
            ids.add(session.getGroupId());
        }
        List<Long> fromJson = parseGroupIdsJson(session.getGroupIdsJson());
        ids.addAll(fromJson);
        Instant now = Instant.now();
        for (Long groupId : ids) {
            UserRecentGroup recent = userRecentGroupRepository
                    .findById(new UserRecentGroupId(userId, groupId))
                    .orElseGet(() -> {
                        UserRecentGroup created = new UserRecentGroup();
                        created.setUserId(userId);
                        created.setGroupId(groupId);
                        return created;
                    });
            recent.setLastStudiedAt(now);
            userRecentGroupRepository.save(recent);
        }
    }

    private StudyGroup requireOwnedGroup(Long userId, Long groupId) {
        return groupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "分组不存在"));
    }

    private static List<Long> normalizeGroupIds(List<Long> groupIds, Long groupId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (groupIds != null) {
            for (Long id : groupIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty() && groupId != null) {
            ids.add(groupId);
        }
        return new ArrayList<>(ids);
    }

    private static QuizSessionSource parseSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return QuizSessionSource.today;
        }
        try {
            return QuizSessionSource.valueOf(raw.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new WordflipException("VALIDATION_ERROR", "无效的 source: " + raw);
        }
    }

    private static QuizLaunchMode parseLaunchMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return QuizLaunchMode.mixed;
        }
        try {
            return QuizLaunchMode.valueOf(raw.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new WordflipException("VALIDATION_ERROR", "无效的 launchMode: " + raw);
        }
    }

    private static List<QuestionType> parseQuestionTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<QuestionType> types = new ArrayList<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            try {
                types.add(QuestionType.valueOf(item.trim().toLowerCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new WordflipException("VALIDATION_ERROR", "无效的 questionType: " + item);
            }
        }
        return types;
    }

    private static QuestionType randomChoiceType() {
        return ThreadLocalRandom.current().nextBoolean()
                ? QuestionType.choice_en_cn
                : QuestionType.choice_cn_en;
    }

    private static QuestionType randomAnyType() {
        int n = ThreadLocalRandom.current().nextInt(3);
        return switch (n) {
            case 0 -> QuestionType.dictation;
            case 1 -> QuestionType.choice_en_cn;
            default -> QuestionType.choice_cn_en;
        };
    }

    private static int normalizeLimit(Integer limit) {
        int value = limit != null ? limit : 10;
        return Math.min(Math.max(value, 1), 50);
    }

    private static WordSummary fallbackSummary(String wordKey) {
        return new WordSummary(wordKey, wordKey, wordKey, null, null);
    }

    /** 解析 options_json 为 DTO；dictation 无选项 */
    private QuizQuestionDto toQuestionDto(QuizQuestion entity) {
        QuestionType type = entity.getQuestionType() != null ? entity.getQuestionType() : QuestionType.dictation;
        List<QuizOptionDto> options = null;
        if (type != QuestionType.dictation && entity.getOptionsJson() != null) {
            options = parseOptionsJson(entity.getOptionsJson());
        }
        // choice_en_cn 题干为英文；其余保留中文释义题干
        String promptEn = type == QuestionType.choice_en_cn ? entity.getExpectedEn() : null;
        QuizPromptDto prompt = new QuizPromptDto(
                entity.getPromptCn(),
                entity.getPromptPos(),
                entity.getPromptPh(),
                promptEn
        );
        return new QuizQuestionDto(
                entity.getQuestionIndex(),
                entity.getWordKey(),
                type.name(),
                prompt,
                options
        );
    }

    private List<QuizOptionDto> parseOptionsJson(String json) {
        try {
            List<QuizOptionDto> list = objectMapper.readValue(json, OPTIONS_TYPE);
            return list != null ? list : List.of();
        } catch (JsonProcessingException ex) {
            throw new WordflipException("INTERNAL_ERROR", "选项解析失败");
        }
    }

    private List<Long> parseGroupIdsJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> list = objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
            return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String toJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new WordflipException("INTERNAL_ERROR", "JSON 序列化失败");
        }
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
