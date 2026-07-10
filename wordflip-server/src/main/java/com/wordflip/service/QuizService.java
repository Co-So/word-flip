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
import com.wordflip.util.WordSenseNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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

        List<String> rawPool = buildPool(userId, source, groupId, groupIds, zoneId);
        if (rawPool.isEmpty()) {
            throw new WordflipException("EMPTY_POOL", "无题可出");
        }

        // 先解析并清洗释义，再过滤不可出题词（无汉字释义 / 短语拆坏虚词）
        Map<String, WordSummary> poolSummaries = normalizeSummaries(
                wordLookupService.resolveWordSummaries(userId, rawPool)
        );
        List<String> pool = rawPool.stream()
                .filter(k -> WordSenseNormalizer.isQuizEligible(
                        poolSummaries.getOrDefault(k, fallbackSummary(k))))
                .collect(Collectors.toCollection(ArrayList::new));
        if (pool.isEmpty()) {
            throw new WordflipException("EMPTY_POOL", "词库释义未清洗完成，暂无合格题目");
        }

        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        int total = Math.min(shuffled.size(), questionLimit);
        List<String> selected = new ArrayList<>(shuffled.subList(0, total));

        List<QuestionType> requestedTypes = parseQuestionTypes(request.getQuestionTypes());
        List<QuestionType> assignedTypes = assignQuestionTypes(total, launchMode, requestedTypes);

        Map<String, WordSummary> summaries = new java.util.HashMap<>();
        for (String key : selected) {
            summaries.put(key, poolSummaries.getOrDefault(key, fallbackSummary(key)));
        }
        // 干扰项从用户全部已入组词抽取（后续再按词性/标签去重筛选）
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
            entity.setExpectedEn(summary.en());
            // 题干/选项一律用清洗后的 cn（词性只走 pos 字段）
            entity.setPromptCn(summary.cn() != null ? summary.cn() : "");
            entity.setPromptPos(summary.pos());
            entity.setPromptPh(summary.ph());

            // 选择题：优质干扰项；若无法凑出互异 label，降级为默写
            if (type != QuestionType.dictation) {
                List<QuizOptionDto> options = buildChoiceOptions(
                        userId, wordKey, type, summary, distractorPool, summaries, poolSummaries);
                if (options.size() < 2 || !hasDistinctLabels(options)) {
                    type = QuestionType.dictation;
                    entity.setQuestionType(type);
                } else {
                    entity.setQuestionType(type);
                    entity.setOptionsJson(toJsonOrNull(options));
                    entity.setCorrectKey(wordKey);
                }
            } else {
                entity.setQuestionType(type);
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
        // 答错：expectedEn 供巩固默写；expectedAnswer 按题型展示（英选中=中文）
        String expectedEn = correct ? null : question.getExpectedEn();
        String expectedAnswer = correct ? null : resolveExpectedAnswer(question, questionType);
        MasteryUpdateDto masteryUpdate = new MasteryUpdateDto(question.getWordKey(), before, after);
        QuizSessionProgressDto sessionProgress = new QuizSessionProgressDto(
                completed ? QuizSessionStatus.completed.name() : QuizSessionStatus.in_progress.name(),
                session.getScore(),
                session.getCurrentIndex(),
                session.getTotalQuestions(),
                nextQuestion
        );
        return new AnswerResultResponse(
                correct, expectedEn, expectedAnswer, feedback, masteryUpdate, sessionProgress);
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

    /**
     * 选择题选项：正确项 + 最多 3 个优质干扰项。
     * 业界实践：同词性优先、label 互异、长度相近、排除无释义/同义重复（University of Waterloo / TELRP）。
     */
    private List<QuizOptionDto> buildChoiceOptions(
            Long userId,
            String correctWordKey,
            QuestionType type,
            WordSummary correctSummary,
            List<String> distractorPool,
            Map<String, WordSummary> knownSummaries,
            Map<String, WordSummary> poolSummaries
    ) {
        QuizOptionDto correctOption = toOption(correctWordKey, correctSummary, type);
        String correctLabelKey = WordSenseNormalizer.labelKey(correctOption.label());
        if (correctLabelKey.isEmpty() || "（无释义）".equals(correctOption.label())) {
            return List.of(correctOption);
        }

        String targetFamily = WordSenseNormalizer.posFamily(correctSummary.pos());
        int targetLen = correctOption.label().length();

        // 先打乱再取样解析，避免全库 lookup
        List<String> sampleKeys = new ArrayList<>(distractorPool);
        Collections.shuffle(sampleKeys);
        List<String> needLookup = sampleKeys.stream()
                .filter(k -> !k.equals(correctWordKey))
                .filter(k -> !knownSummaries.containsKey(k) && !poolSummaries.containsKey(k))
                .limit(64)
                .toList();
        if (!needLookup.isEmpty()) {
            knownSummaries.putAll(normalizeSummaries(wordLookupService.resolveWordSummaries(userId, needLookup)));
        }

        List<ScoredDistractor> scored = new ArrayList<>();
        Set<String> seenLabels = new HashSet<>();
        seenLabels.add(correctLabelKey);

        for (String key : sampleKeys) {
            if (key.equals(correctWordKey)) {
                continue;
            }
            WordSummary summary = knownSummaries.get(key);
            if (summary == null) {
                summary = poolSummaries.get(key);
            }
            if (summary == null) {
                continue;
            }
            summary = WordSenseNormalizer.normalizeSummary(summary);
            if (!WordSenseNormalizer.isQuizEligible(summary)) {
                continue;
            }
            QuizOptionDto option = toOption(key, summary, type);
            String labelKey = WordSenseNormalizer.labelKey(option.label());
            if (labelKey.isEmpty() || "（无释义）".equals(option.label()) || !seenLabels.add(labelKey)) {
                continue;
            }
            int score = 0;
            if (WordSenseNormalizer.posFamily(summary.pos()).equals(targetFamily)) {
                score += 100;
            }
            int lenDiff = Math.abs(option.label().length() - targetLen);
            score += Math.max(0, 40 - lenDiff);
            scored.add(new ScoredDistractor(score, option));
            if (scored.size() >= 40) {
                break;
            }
        }

        scored.sort(Comparator.comparingInt(ScoredDistractor::score).reversed());
        int topN = Math.min(12, scored.size());
        if (topN > 1) {
            Collections.shuffle(scored.subList(0, topN));
        }

        List<QuizOptionDto> options = new ArrayList<>();
        options.add(correctOption);
        for (ScoredDistractor d : scored) {
            if (options.size() >= 4) {
                break;
            }
            options.add(d.option());
        }
        Collections.shuffle(options);
        return options;
    }

    private static QuizOptionDto toOption(String wordKey, WordSummary summary, QuestionType type) {
        // choice_en_cn：选项为清洗后中文；choice_cn_en：选项为英文；key 统一用 wordKey
        if (type == QuestionType.choice_en_cn) {
            String cn = WordSenseNormalizer.cleanDisplayCn(summary.cn());
            String label = (WordSenseNormalizer.hasHan(cn) && !cn.equalsIgnoreCase(wordKey))
                    ? cn
                    : "（无释义）";
            return new QuizOptionDto(wordKey, label);
        }
        String en = summary.en();
        String label = (en != null && !en.isBlank()) ? en : wordKey;
        return new QuizOptionDto(wordKey, label);
    }

    /** 答错时按题型给出用户可读的正确答案 */
    private static String resolveExpectedAnswer(QuizQuestion question, QuestionType type) {
        if (type == QuestionType.choice_en_cn) {
            String cn = WordSenseNormalizer.cleanDisplayCn(question.getPromptCn());
            if (WordSenseNormalizer.hasHan(cn)) {
                return cn;
            }
        }
        return question.getExpectedEn();
    }

    private static Map<String, WordSummary> normalizeSummaries(Map<String, WordSummary> raw) {
        Map<String, WordSummary> out = new java.util.HashMap<>();
        for (Map.Entry<String, WordSummary> e : raw.entrySet()) {
            out.put(e.getKey(), WordSenseNormalizer.normalizeSummary(e.getValue()));
        }
        return out;
    }

    private static boolean hasDistinctLabels(List<QuizOptionDto> options) {
        Set<String> keys = new HashSet<>();
        for (QuizOptionDto o : options) {
            String k = WordSenseNormalizer.labelKey(o.label());
            if (k.isEmpty() || !keys.add(k)) {
                return false;
            }
        }
        return keys.size() >= 2;
    }

    private record ScoredDistractor(int score, QuizOptionDto option) {
    }

    /** 缺词义时 en 回退 wordKey，cn 留空（避免选择题把英文当中文选项） */
    private static WordSummary fallbackSummary(String wordKey) {
        return new WordSummary(wordKey, wordKey, "", null, null);
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
