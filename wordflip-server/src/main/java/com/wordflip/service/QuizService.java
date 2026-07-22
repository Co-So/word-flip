package com.wordflip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.dto.learning.FsrsMemoryResponse;
import com.wordflip.dto.quiz.AnswerResultResponse;
import com.wordflip.dto.quiz.CreateQuizSessionRequest;
import com.wordflip.dto.quiz.MasteryUpdateDto;
import com.wordflip.dto.quiz.QuizOptionDto;
import com.wordflip.dto.quiz.QuizPromptDto;
import com.wordflip.dto.quiz.QuizQuestionDto;
import com.wordflip.dto.quiz.QuizResultResponse;
import com.wordflip.dto.quiz.QuizSessionCreatedResponse;
import com.wordflip.dto.quiz.QuizSessionProgressDto;
import com.wordflip.dto.quiz.QuizWrongCardDto;
import com.wordflip.dto.quiz.SubmitAnswerRequest;
import com.wordflip.exception.WordflipException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学习卡测验编排：题目快照、服务端判题、requestId 幂等与 FSRS 原子更新。
 */
@Service
public class QuizService {

    private static final Set<String> QUESTION_TYPES = Set.of(
            "dictation", "choice_en_cn", "choice_cn_en"
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final FsrsReviewService reviewService;

    public QuizService(JdbcTemplate jdbc, ObjectMapper objectMapper, FsrsReviewService reviewService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.reviewService = reviewService;
    }

    /**
     * 从当前计划已发布且已入组的学习卡创建会话，并保存题面与答案快照。
     */
    @Transactional
    public QuizSessionCreatedResponse createSession(
            Long userId, CreateQuizSessionRequest request, ZoneId zoneId
    ) {
        Long planId = currentPlanId(userId);
        String source = normalizeSource(request.getSource());
        List<Long> groupIds = normalizeGroups(request);
        if (("study".equals(source) || "groups".equals(source)) && groupIds.isEmpty()) {
            throw new WordflipException("VALIDATION_ERROR", source + " 源须指定分组");
        }
        groupIds.forEach(groupId -> requireGroup(userId, planId, groupId));
        int limit = Math.min(Math.max(request.getQuestionLimit() == null ? 10 : request.getQuestionLimit(), 1), 50);
        List<CardCandidate> pool = loadPool(userId, planId, source, groupIds);
        if (pool.isEmpty()) {
            throw new WordflipException("EMPTY_POOL", "当前计划没有可测验的已发布学习卡");
        }
        Collections.shuffle(pool);
        List<CardCandidate> selected = pool.subList(0, Math.min(limit, pool.size()));
        List<String> types = normalizeQuestionTypes(request.getQuestionTypes(), selected.size());

        String sessionId = UUID.randomUUID().toString();
        jdbc.update(
                """
                INSERT INTO quiz_sessions(id, user_id, plan_id, status, source, question_count, score)
                VALUES (?, ?, ?, 'in_progress', ?, ?, 0)
                """,
                sessionId, userId, planId, source, selected.size()
        );
        for (int index = 0; index < selected.size(); index++) {
            CardCandidate card = selected.get(index);
            String type = types.get(index);
            List<QuizOptionDto> options = "dictation".equals(type)
                    ? null : buildOptions(planId, card, type);
            if (options != null && options.size() < 2) {
                type = "dictation";
                options = null;
            }
            PromptSnapshot prompt = new PromptSnapshot(
                    card.cn(), card.pos(), card.phonetic(), "choice_en_cn".equals(type) ? card.en() : null,
                    options
            );
            AnswerSnapshot answer = new AnswerSnapshot(card.en(), card.cn(), card.wordKey());
            jdbc.update(
                    """
                    INSERT INTO quiz_questions(
                      session_id, card_id, lexeme_id, skill, question_type,
                      prompt_json, answer_json, sort_order
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    sessionId, card.cardId(), card.lexemeId(), skill(type), type,
                    writeJson(prompt), writeJson(answer), index
            );
        }
        return new QuizSessionCreatedResponse(
                sessionId, "in_progress", selected.size(), 0, 0, loadQuestion(sessionId, 0)
        );
    }

    /**
     * 同一事务完成判题、答案、复习事件、双层记忆和会话进度更新。
     */
    @Transactional
    public AnswerResultResponse submitAnswer(
            Long userId, String sessionId, SubmitAnswerRequest request, ZoneId zoneId
    ) {
        List<String> duplicate = jdbc.queryForList(
                "SELECT response_json FROM quiz_answers WHERE request_id=? AND user_id=?",
                String.class, request.requestId().toString(), userId
        );
        if (!duplicate.isEmpty()) {
            return readJson(duplicate.getFirst(), AnswerResultResponse.class);
        }
        SessionRow session = loadSessionForUpdate(userId, sessionId);
        if (!"in_progress".equals(session.status()) || request.questionIndex() != session.currentIndex()) {
            throw new WordflipException("CONFLICT", "会话已完成或题号不匹配");
        }
        QuestionRow question = loadQuestionRow(sessionId, request.questionIndex());
        UserAnswer userAnswer = new UserAnswer(request.answer(), request.selectedKey());
        boolean correct = isCorrect(question, userAnswer);
        Instant answeredAt = Instant.now();
        FsrsScheduleResult result = reviewService.applyQuizResult(new FsrsReviewCommand(
                request.requestId(), userId, session.planId(), question.cardId(), question.lexemeId(),
                question.skill(), question.type(), correct, answeredAt
        ));
        Long reviewEventId = jdbc.queryForObject(
                "SELECT id FROM review_events WHERE request_id=?", Long.class, request.requestId().toString()
        );

        int nextIndex = session.currentIndex() + 1;
        int score = session.score() + (correct ? 1 : 0);
        boolean completed = nextIndex >= session.total();
        jdbc.update(
                "UPDATE quiz_sessions SET status=?, score=?, completed_at=? WHERE id=? AND user_id=?",
                completed ? "completed" : "in_progress", score,
                completed ? Timestamp.from(answeredAt) : null, sessionId, userId
        );
        QuizQuestionDto next = completed ? null : loadQuestion(sessionId, nextIndex);
        AnswerSnapshot expected = readJson(question.answerJson(), AnswerSnapshot.class);
        AnswerResultResponse response = new AnswerResultResponse(
                correct,
                correct ? null : expected.expectedEn(),
                correct ? null : ("choice_en_cn".equals(question.type())
                        ? expected.expectedCn() : expected.expectedEn()),
                correct ? "correct" : "wrong",
                new MasteryUpdateDto(
                        question.cardId(), question.lexemeId(), toMemory(result.before()), toMemory(result.after())
                ),
                new QuizSessionProgressDto(
                        completed ? "completed" : "in_progress", score, nextIndex, session.total(), next
                )
        );
        jdbc.update(
                """
                INSERT INTO quiz_answers(
                  request_id, session_id, question_id, user_id, answer_json, response_json,
                  correct, review_event_id, answered_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                request.requestId().toString(), sessionId, question.id(), userId,
                writeJson(userAnswer), writeJson(response), correct, reviewEventId, Timestamp.from(answeredAt)
        );
        if (completed) {
            jdbc.update(
                    """
                    INSERT INTO study_logs(user_id, plan_id, log_date, quiz_count)
                    VALUES (?, ?, ?, ?)
                    """,
                    userId, session.planId(), Date.valueOf(answeredAt.atZone(zoneId).toLocalDate()), session.total()
            );
        }
        return response;
    }

    @Transactional(readOnly = true)
    public QuizResultResponse getResult(Long userId, String sessionId) {
        SessionRow session = loadSession(userId, sessionId);
        if (!"completed".equals(session.status())) {
            throw new WordflipException("CONFLICT", "测验尚未完成");
        }
        List<QuizWrongCardDto> wrong = jdbc.query(
                """
                SELECT q.card_id, q.lexeme_id, q.answer_json, a.answer_json AS user_answer
                  FROM quiz_answers a JOIN quiz_questions q ON q.id=a.question_id
                 WHERE a.session_id=? AND a.correct=FALSE ORDER BY q.sort_order
                """,
                (rs, row) -> {
                    AnswerSnapshot expected = readJson(rs.getString("answer_json"), AnswerSnapshot.class);
                    UserAnswer answer = readJson(rs.getString("user_answer"), UserAnswer.class);
                    String submitted = answer.answer() == null ? answer.selectedKey() : answer.answer();
                    return new QuizWrongCardDto(
                            rs.getLong("card_id"), rs.getLong("lexeme_id"),
                            expected.expectedEn(), expected.expectedCn(), submitted
                    );
                },
                sessionId
        );
        int correct = session.score();
        int wrongCount = session.total() - correct;
        float accuracy = session.total() == 0 ? 0 : (float) correct / session.total();
        String rating = accuracy >= 0.9 ? "excellent" : accuracy >= 0.7 ? "good" : "keep_going";
        return new QuizResultResponse(
                sessionId, correct, session.total(), correct, wrongCount, accuracy, rating, wrong
        );
    }

    private List<CardCandidate> loadPool(Long userId, Long planId, String source, List<Long> groups) {
        String select = """
                SELECT DISTINCT c.id AS card_id, l.id AS lexeme_id, l.word_key, l.headword,
                       l.phonetic, s.cn, s.pos, s.en_gloss, sgc.group_id
                  FROM study_group_cards sgc
                  JOIN learning_cards c ON c.id=sgc.card_id AND c.status='published'
                  JOIN book_items bi ON bi.id=c.book_item_id
                  JOIN lexemes l ON l.id=bi.lexeme_id
                  JOIN learning_card_senses s ON s.card_id=c.id AND s.is_primary=TRUE AND s.quality='ok'
                 WHERE sgc.plan_id=?
                """;
        List<CardCandidate> all = jdbc.query(select + " ORDER BY c.id", this::mapCandidate, planId);
        return all.stream().filter(card -> groups.isEmpty() || groups.contains(card.groupId()))
                .filter(card -> {
                    if ("diagnostic".equals(source)) {
                        Integer familiar = jdbc.queryForObject(
                                """
                                SELECT COUNT(*) FROM lexeme_skill_memory lm
                                WHERE lm.user_id=? AND lm.lexeme_id=? AND lm.familiarity>0
                                  AND NOT EXISTS(
                                    SELECT 1 FROM review_events r
                                    WHERE r.user_id=lm.user_id AND r.card_id=? AND r.skill=lm.skill
                                  )
                                """,
                                Integer.class, userId, card.lexemeId(), card.cardId()
                        );
                        return familiar != null && familiar > 0;
                    }
                    if (!"today".equals(source) && !"retry".equals(source)) {
                        return true;
                    }
                    Integer count = jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM card_skill_memory
                            WHERE user_id=? AND card_id=? AND due_at>NOW(3)
                            """,
                            Integer.class, userId, card.cardId()
                    );
                    return count == null || count < 2;
                }).toList();
    }

    private List<QuizOptionDto> buildOptions(Long planId, CardCandidate correct, String type) {
        List<CardCandidate> choices = jdbc.query(
                """
                SELECT DISTINCT c.id AS card_id, l.id AS lexeme_id, l.word_key, l.headword,
                       l.phonetic, s.cn, s.pos, s.en_gloss, sgc.group_id
                  FROM study_group_cards sgc
                  JOIN learning_cards c ON c.id=sgc.card_id AND c.status='published'
                  JOIN book_items bi ON bi.id=c.book_item_id
                  JOIN lexemes l ON l.id=bi.lexeme_id
                  JOIN learning_card_senses s ON s.card_id=c.id AND s.is_primary=TRUE AND s.quality='ok'
                 WHERE sgc.plan_id=? AND c.id<>? ORDER BY RAND() LIMIT 12
                """,
                this::mapCandidate, planId, correct.cardId()
        );
        List<QuizOptionDto> options = new ArrayList<>();
        Set<String> labels = new LinkedHashSet<>();
        addOption(options, labels, correct, type);
        for (CardCandidate candidate : choices) {
            addOption(options, labels, candidate, type);
            if (options.size() == 4) {
                break;
            }
        }
        Collections.shuffle(options);
        return options;
    }

    private void addOption(
            List<QuizOptionDto> options, Set<String> labels, CardCandidate card, String type
    ) {
        String label = "choice_en_cn".equals(type) ? card.cn() : card.en();
        if (label != null && !label.isBlank() && labels.add(label.trim().toLowerCase(Locale.ROOT))) {
            options.add(new QuizOptionDto(card.wordKey(), label));
        }
    }

    private boolean isCorrect(QuestionRow question, UserAnswer userAnswer) {
        AnswerSnapshot expected = readJson(question.answerJson(), AnswerSnapshot.class);
        if ("dictation".equals(question.type())) {
            return userAnswer.answer() != null
                    && expected.expectedEn().trim().equalsIgnoreCase(userAnswer.answer().trim());
        }
        return userAnswer.selectedKey() != null
                && expected.correctKey().equalsIgnoreCase(userAnswer.selectedKey().trim());
    }

    private QuizQuestionDto loadQuestion(String sessionId, int index) {
        QuestionRow row = loadQuestionRow(sessionId, index);
        PromptSnapshot prompt = readJson(row.promptJson(), PromptSnapshot.class);
        return new QuizQuestionDto(
                index, row.cardId(), row.lexemeId(), row.type(),
                new QuizPromptDto(prompt.cn(), prompt.pos(), prompt.ph(), prompt.en()), prompt.options()
        );
    }

    private QuestionRow loadQuestionRow(String sessionId, int index) {
        List<QuestionRow> rows = jdbc.query(
                """
                SELECT id, card_id, lexeme_id, skill, question_type, prompt_json, answer_json
                  FROM quiz_questions WHERE session_id=? AND sort_order=?
                """,
                (rs, row) -> new QuestionRow(
                        rs.getLong("id"), rs.getLong("card_id"), rs.getLong("lexeme_id"),
                        rs.getString("skill"), rs.getString("question_type"),
                        rs.getString("prompt_json"), rs.getString("answer_json")
                ), sessionId, index
        );
        if (rows.isEmpty()) {
            throw new WordflipException("NOT_FOUND", "测验题目不存在");
        }
        return rows.getFirst();
    }

    private SessionRow loadSessionForUpdate(Long userId, String sessionId) {
        List<SessionRow> rows = jdbc.query(
                """
                SELECT s.id, s.plan_id, s.status, s.question_count, s.score,
                       (SELECT COUNT(*) FROM quiz_answers a WHERE a.session_id=s.id) AS current_index
                  FROM quiz_sessions s WHERE s.id=? AND s.user_id=? FOR UPDATE
                """,
                this::mapSession, sessionId, userId
        );
        if (rows.isEmpty()) {
            throw new WordflipException("NOT_FOUND", "测验会话不存在");
        }
        return rows.getFirst();
    }

    private SessionRow loadSession(Long userId, String sessionId) {
        List<SessionRow> rows = jdbc.query(
                """
                SELECT s.id, s.plan_id, s.status, s.question_count, s.score,
                       (SELECT COUNT(*) FROM quiz_answers a WHERE a.session_id=s.id) AS current_index
                  FROM quiz_sessions s WHERE s.id=? AND s.user_id=?
                """,
                this::mapSession, sessionId, userId
        );
        if (rows.isEmpty()) {
            throw new WordflipException("NOT_FOUND", "测验会话不存在");
        }
        return rows.getFirst();
    }

    private SessionRow mapSession(ResultSet rs, int row) throws SQLException {
        return new SessionRow(
                rs.getString("id"), rs.getLong("plan_id"), rs.getString("status"),
                rs.getInt("question_count"), rs.getInt("score"), rs.getInt("current_index")
        );
    }

    private CardCandidate mapCandidate(ResultSet rs, int row) throws SQLException {
        return new CardCandidate(
                rs.getLong("card_id"), rs.getLong("lexeme_id"), rs.getString("word_key"),
                rs.getString("headword"), rs.getString("phonetic"), rs.getString("cn"),
                rs.getString("pos"), rs.getString("en_gloss"), rs.getLong("group_id")
        );
    }

    private Long currentPlanId(Long userId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT active_plan_id FROM user_settings WHERE user_id=? AND active_plan_id IS NOT NULL",
                Long.class, userId
        );
        if (ids.isEmpty()) {
            throw new WordflipException("NOT_FOUND", "尚未选择当前学习计划");
        }
        return ids.getFirst();
    }

    private void requireGroup(Long userId, Long planId, Long groupId) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM study_groups g JOIN user_learning_plans p ON p.id=g.plan_id
                WHERE g.id=? AND g.plan_id=? AND p.user_id=?
                """,
                Integer.class, groupId, planId, userId
        );
        if (count == null || count == 0) {
            throw new WordflipException("NOT_FOUND", "分组不属于当前学习计划");
        }
    }

    private List<Long> normalizeGroups(CreateQuizSessionRequest request) {
        LinkedHashSet<Long> values = new LinkedHashSet<>();
        if (request.getGroupIds() != null) {
            request.getGroupIds().stream().filter(java.util.Objects::nonNull).forEach(values::add);
        }
        if (request.getGroupId() != null) {
            values.add(request.getGroupId());
        }
        return List.copyOf(values);
    }

    private String normalizeSource(String raw) {
        String value = raw == null || raw.isBlank() ? "today" : raw.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("today", "study", "retry", "groups", "all", "recent", "diagnostic").contains(value)) {
            throw new WordflipException("VALIDATION_ERROR", "无效的 source: " + raw);
        }
        return value;
    }

    private List<String> normalizeQuestionTypes(List<String> requested, int total) {
        List<String> valid = requested == null ? List.of() : requested.stream()
                .filter(java.util.Objects::nonNull).map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(QUESTION_TYPES::contains).toList();
        List<String> values = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            values.add(valid.isEmpty() ? (index % 2 == 0 ? "dictation" : "choice_en_cn")
                    : valid.get(index % valid.size()));
        }
        return values;
    }

    private static String skill(String questionType) {
        return "dictation".equals(questionType) ? "dictation" : "choice";
    }

    private FsrsMemoryResponse toMemory(FsrsMemorySnapshot snapshot) {
        return new FsrsMemoryResponse(
                snapshot.state(), snapshot.dueAt(), snapshot.stability(), snapshot.difficulty(),
                snapshot.reps(), snapshot.lapses()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("测验快照序列化失败", error);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("测验快照解析失败", error);
        }
    }

    private record CardCandidate(
            Long cardId, Long lexemeId, String wordKey, String en, String phonetic,
            String cn, String pos, String enGloss, Long groupId
    ) {
    }

    private record PromptSnapshot(
            String cn, String pos, String ph, String en, List<QuizOptionDto> options
    ) {
    }

    private record AnswerSnapshot(String expectedEn, String expectedCn, String correctKey) {
    }

    private record UserAnswer(String answer, String selectedKey) {
    }

    private record QuestionRow(
            Long id, Long cardId, Long lexemeId, String skill, String type,
            String promptJson, String answerJson
    ) {
    }

    private record SessionRow(
            String id, Long planId, String status, int total, int score, int currentIndex
    ) {
    }
}
