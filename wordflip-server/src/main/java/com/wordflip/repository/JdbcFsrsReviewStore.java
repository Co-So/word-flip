package com.wordflip.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.service.FsrsMemorySnapshot;
import com.wordflip.service.FsrsReviewCommand;
import com.wordflip.service.FsrsScheduleResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 通过行锁实现 FSRS 双层记忆原子更新和请求幂等。
 */
@Repository
public class JdbcFsrsReviewStore implements FsrsReviewStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcFsrsReviewStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<FsrsScheduleResult> findByRequestId(UUID requestId) {
        List<FsrsScheduleResult> values = jdbc.query(
                "SELECT rating, old_state_json, new_state_json FROM review_events WHERE request_id=?",
                (rs, row) -> new FsrsScheduleResult(
                        rs.getString("rating"),
                        readSnapshot(rs.getString("old_state_json")),
                        readSnapshot(rs.getString("new_state_json"))
                ),
                requestId.toString()
        );
        return values.stream().findFirst();
    }

    @Override
    public FsrsMemorySnapshot lockCardMemory(Long userId, Long cardId, String skill, Instant now) {
        jdbc.update(
                """
                INSERT INTO card_skill_memory(user_id, card_id, skill, state, due_at)
                VALUES (?, ?, ?, 'new', ?)
                ON DUPLICATE KEY UPDATE id=id
                """,
                userId,
                cardId,
                skill,
                Timestamp.from(now)
        );
        return jdbc.queryForObject(
                """
                SELECT state, step, stability, difficulty, due_at, last_review_at,
                       reps, lapses, elapsed_days, scheduled_days
                  FROM card_skill_memory
                 WHERE user_id=? AND card_id=? AND skill=? FOR UPDATE
                """,
                (rs, row) -> mapSnapshot(rs),
                userId,
                cardId,
                skill
        );
    }

    @Override
    public void lockLexemeMemory(Long userId, Long lexemeId, String skill) {
        jdbc.update(
                """
                INSERT INTO lexeme_skill_memory(user_id, lexeme_id, skill)
                VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE id=id
                """,
                userId,
                lexemeId,
                skill
        );
        jdbc.queryForObject(
                "SELECT id FROM lexeme_skill_memory WHERE user_id=? AND lexeme_id=? AND skill=? FOR UPDATE",
                Long.class,
                userId,
                lexemeId,
                skill
        );
    }

    @Override
    public void insertReviewEvent(FsrsReviewCommand command, FsrsScheduleResult result) {
        jdbc.update(
                """
                INSERT INTO review_events(
                  request_id, user_id, plan_id, card_id, lexeme_id, skill, question_type,
                  rating, correct, answered_at, old_state_json, new_state_json, fsrs_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '1.0.0')
                """,
                command.requestId().toString(), command.userId(), command.planId(),
                command.cardId(), command.lexemeId(), command.skill(), command.questionType(),
                result.rating(), command.correct(), Timestamp.from(command.answeredAt()),
                writeJson(result.before()), writeJson(result.after())
        );
    }

    @Override
    public void updateCardMemory(Long userId, Long cardId, String skill, FsrsMemorySnapshot after) {
        jdbc.update(
                """
                UPDATE card_skill_memory
                   SET state=?, step=?, stability=?, difficulty=?, due_at=?, last_review_at=?,
                       reps=?, lapses=?, elapsed_days=?, scheduled_days=?, fsrs_version='1.0.0', version=version+1
                 WHERE user_id=? AND card_id=? AND skill=?
                """,
                after.state(), after.step(), after.stability(), after.difficulty(),
                Timestamp.from(after.dueAt()), timestamp(after.lastReviewAt()),
                after.reps(), after.lapses(), after.elapsedDays(), after.scheduledDays(),
                userId, cardId, skill
        );
    }

    @Override
    public void updateLexemeMemory(
            Long userId,
            Long lexemeId,
            String skill,
            boolean correct,
            Instant answeredAt
    ) {
        jdbc.update(
                """
                UPDATE lexeme_skill_memory
                   SET familiarity=CASE WHEN ? THEN familiarity + (1-familiarity)*0.10 ELSE familiarity*0.70 END,
                       last_review_at=?,
                       successful_reviews=successful_reviews + CASE WHEN ? THEN 1 ELSE 0 END,
                       failed_reviews=failed_reviews + CASE WHEN ? THEN 0 ELSE 1 END,
                       version=version+1
                 WHERE user_id=? AND lexeme_id=? AND skill=?
                """,
                correct, Timestamp.from(answeredAt), correct, correct, userId, lexemeId, skill
        );
    }

    private FsrsMemorySnapshot mapSnapshot(ResultSet rs) throws SQLException {
        Timestamp lastReview = rs.getTimestamp("last_review_at");
        return new FsrsMemorySnapshot(
                rs.getString("state"), rs.getObject("step", Integer.class),
                rs.getDouble("stability"), rs.getDouble("difficulty"),
                rs.getTimestamp("due_at").toInstant(),
                lastReview == null ? null : lastReview.toInstant(),
                rs.getInt("reps"), rs.getInt("lapses"),
                rs.getInt("elapsed_days"), rs.getInt("scheduled_days")
        );
    }

    private FsrsMemorySnapshot readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, FsrsMemorySnapshot.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法读取复习事件状态快照", error);
        }
    }

    private String writeJson(FsrsMemorySnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法写入复习事件状态快照", error);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
