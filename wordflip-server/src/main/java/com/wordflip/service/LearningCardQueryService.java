package com.wordflip.service;

import com.wordflip.dto.learning.BookCardsResponse;
import com.wordflip.dto.learning.CardProgressResponse;
import com.wordflip.dto.learning.CurrentWordResponse;
import com.wordflip.dto.learning.FsrsMemoryResponse;
import com.wordflip.dto.learning.LearningCardDetailResponse;
import com.wordflip.dto.learning.LearningCardExampleResponse;
import com.wordflip.dto.learning.LearningCardResponse;
import com.wordflip.dto.learning.LearningCardSenseResponse;
import com.wordflip.dto.learning.SourceMaterialResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 读取词书专属学习卡；默认考义与词典资料严格分区。
 */
@Service
public class LearningCardQueryService {

    private static final String CARD_SELECT = """
            SELECT c.id AS card_id, l.id AS lexeme_id, bi.book_id, l.word_key,
                   l.headword, COALESCE(l.phonetic, '') AS phonetic, c.version
              FROM learning_cards c
              JOIN book_items bi ON bi.id=c.book_item_id
              JOIN lexemes l ON l.id=bi.lexeme_id
            """;

    private final JdbcTemplate jdbc;

    public LearningCardQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public BookCardsResponse listBookCards(Long userId, Long bookId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Integer visible = jdbc.queryForObject(
                "SELECT COUNT(*) FROM books WHERE id=? AND status='published' AND (visibility='public' OR owner_user_id=?)",
                Integer.class,
                bookId,
                userId
        );
        if (visible == null || visible == 0) {
            throw new IllegalArgumentException("词书不存在或不可见");
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_cards c JOIN book_items bi ON bi.id=c.book_item_id WHERE bi.book_id=? AND c.status='published'",
                Long.class,
                bookId
        );
        List<LearningCardResponse> cards = jdbc.query(
                CARD_SELECT + " WHERE bi.book_id=? AND c.status='published' ORDER BY bi.sort_order LIMIT ? OFFSET ?",
                (rs, row) -> mapCard(rs),
                bookId,
                safeSize,
                (safePage - 1) * safeSize
        );
        long count = total == null ? 0 : total;
        return new BookCardsResponse(
                safePage, safeSize, count, (int) Math.ceil((double) count / safeSize), hydrateSenses(cards)
        );
    }

    @Transactional(readOnly = true)
    public LearningCardDetailResponse getCurrentCard(Long userId, Long cardId) {
        LearningCardResponse card = oneCard(
                CARD_SELECT + " JOIN user_learning_plans p ON p.book_id=bi.book_id"
                        + " JOIN user_settings us ON us.active_plan_id=p.id AND us.user_id=p.user_id"
                        + " WHERE c.id=? AND c.status='published' AND p.user_id=?",
                cardId,
                userId
        );
        card = withSenses(card);
        return LearningCardDetailResponse.from(
                card, loadProgress(userId, cardId), loadSources(card.lexemeId())
        );
    }

    @Transactional(readOnly = true)
    public CurrentWordResponse lookupCurrentWord(Long userId, String wordKey) {
        String normalized = wordKey.trim().toLowerCase();
        LearningCardResponse card = oneCard(
                CARD_SELECT + " JOIN user_learning_plans p ON p.book_id=bi.book_id"
                        + " JOIN user_settings us ON us.active_plan_id=p.id AND us.user_id=p.user_id"
                        + " WHERE l.word_key=? AND c.status='published' AND p.user_id=?",
                normalized,
                userId
        );
        card = withSenses(card);
        return new CurrentWordResponse(
                card.lexemeId(), card.wordKey(), card.en(), card, loadSources(card.lexemeId())
        );
    }

    private LearningCardResponse oneCard(String sql, Object... args) {
        List<LearningCardResponse> cards = jdbc.query(sql, (rs, row) -> mapCard(rs), args);
        if (cards.isEmpty()) {
            throw new IllegalArgumentException("当前学习计划中没有该学习卡");
        }
        return cards.getFirst();
    }

    private List<LearningCardResponse> hydrateSenses(List<LearningCardResponse> cards) {
        return cards.stream().map(this::withSenses).toList();
    }

    private LearningCardResponse withSenses(LearningCardResponse card) {
        List<LearningCardSenseResponse> senses = jdbc.query(
                """
                SELECT id, pos, cn, en_gloss, is_primary, quality, sort_order
                  FROM learning_card_senses
                 WHERE card_id=? ORDER BY sort_order
                """,
                (rs, row) -> new LearningCardSenseResponse(
                        rs.getLong("id"), rs.getString("pos"), rs.getString("cn"),
                        rs.getString("en_gloss"), rs.getBoolean("is_primary"),
                        rs.getString("quality"), rs.getInt("sort_order"),
                        loadCardExamples(rs.getLong("id"))
                ),
                card.cardId()
        );
        return new LearningCardResponse(
                card.cardId(), card.lexemeId(), card.bookId(), card.wordKey(), card.en(),
                card.phonetic(), card.version(), senses
        );
    }

    private List<LearningCardExampleResponse> loadCardExamples(Long senseId) {
        return jdbc.query(
                "SELECT en, cn, sort_order FROM learning_card_examples WHERE card_sense_id=? ORDER BY sort_order",
                (rs, row) -> new LearningCardExampleResponse(
                        rs.getString("en"), rs.getString("cn"), rs.getInt("sort_order")
                ),
                senseId
        );
    }

    private CardProgressResponse loadProgress(Long userId, Long cardId) {
        Map<String, FsrsMemoryResponse> values = new LinkedHashMap<>();
        jdbc.query(
                "SELECT skill, state, due_at, stability, difficulty, reps, lapses FROM card_skill_memory WHERE user_id=? AND card_id=?",
                (RowCallbackHandler) rs -> values.put(
                        rs.getString("skill"),
                        new FsrsMemoryResponse(
                                rs.getString("state"), rs.getTimestamp("due_at").toInstant(),
                                rs.getDouble("stability"), rs.getDouble("difficulty"),
                                rs.getInt("reps"), rs.getInt("lapses")
                        )
                ),
                userId,
                cardId
        );
        Instant now = Instant.now();
        FsrsMemoryResponse empty = new FsrsMemoryResponse("new", now, 0, 0, 0, 0);
        FsrsMemoryResponse dictation = values.getOrDefault("dictation", empty);
        FsrsMemoryResponse choice = values.getOrDefault("choice", empty);
        // 分组热力由服务端统一计算；客户端只展示，不复制稳定性分档规则。
        return new CardProgressResponse(dictation, choice, displayHeatLevel(dictation.stability()));
    }

    private int displayHeatLevel(double stability) {
        if (stability <= 0) {
            return 0;
        }
        if (stability < 3) {
            return 1;
        }
        if (stability < 15) {
            return 2;
        }
        if (stability < 30) {
            return 3;
        }
        return 4;
    }

    private List<SourceMaterialResponse> loadSources(Long lexemeId) {
        List<SourceMaterialRow> rows = jdbc.query(
                """
                SELECT se.id AS entry_id, cs.code, cs.name, cs.license_name, sr.version,
                       ds.id AS sense_id, ds.pos, ds.cn, ds.en_gloss, ds.quality, ds.sort_order
                  FROM source_entries se
                  JOIN source_revisions sr ON sr.id=se.revision_id
                  JOIN content_sources cs ON cs.id=sr.source_id
                  LEFT JOIN dictionary_senses ds ON ds.source_entry_id=se.id
                 WHERE se.lexeme_id=?
                 ORDER BY cs.code, sr.version, se.id, ds.sort_order
                """,
                (rs, row) -> mapSourceRow(rs),
                lexemeId
        );
        Map<Long, SourceAccumulator> grouped = new LinkedHashMap<>();
        for (SourceMaterialRow row : rows) {
            SourceAccumulator value = grouped.computeIfAbsent(
                    row.entryId(),
                    ignored -> new SourceAccumulator(row.sourceId(), row.sourceName(), row.revision(), row.license(), row.entryId())
            );
            if (row.sense() != null) {
                value.senses.add(row.sense());
            }
        }
        return grouped.values().stream().map(SourceAccumulator::toResponse).toList();
    }

    private LearningCardResponse mapCard(ResultSet rs) throws SQLException {
        return new LearningCardResponse(
                rs.getLong("card_id"), rs.getLong("lexeme_id"), rs.getLong("book_id"),
                rs.getString("word_key"), rs.getString("headword"), rs.getString("phonetic"),
                rs.getInt("version"), List.of()
        );
    }

    private SourceMaterialRow mapSourceRow(ResultSet rs) throws SQLException {
        Long senseId = rs.getObject("sense_id", Long.class);
        LearningCardSenseResponse sense = senseId == null ? null : new LearningCardSenseResponse(
                senseId, rs.getString("pos"), rs.getString("cn"), rs.getString("en_gloss"),
                false, rs.getString("quality"), rs.getInt("sort_order"), List.of()
        );
        return new SourceMaterialRow(
                rs.getLong("entry_id"), rs.getString("code"), rs.getString("name"),
                rs.getString("version"), rs.getString("license_name"), sense
        );
    }

    private record SourceMaterialRow(
            Long entryId, String sourceId, String sourceName, String revision,
            String license, LearningCardSenseResponse sense
    ) {
    }

    private static final class SourceAccumulator {
        private final String sourceId;
        private final String sourceName;
        private final String revision;
        private final String license;
        private final Long entryId;
        private final List<LearningCardSenseResponse> senses = new ArrayList<>();

        private SourceAccumulator(String sourceId, String sourceName, String revision, String license, Long entryId) {
            this.sourceId = sourceId;
            this.sourceName = sourceName;
            this.revision = revision;
            this.license = license;
            this.entryId = entryId;
        }

        private SourceMaterialResponse toResponse() {
            return new SourceMaterialResponse(sourceId, sourceName, revision, license, entryId, List.copyOf(senses));
        }
    }
}
