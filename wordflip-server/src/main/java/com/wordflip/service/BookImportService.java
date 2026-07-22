package com.wordflip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.dto.book.BookImportConfirmResponse;
import com.wordflip.dto.book.BookImportPreviewResponse;
import com.wordflip.dto.book.BookListResponse;
import com.wordflip.dto.word.WordSummary;
import com.wordflip.exception.WordflipException;
import com.wordflip.util.WordKeyUtil;
import com.wordflip.util.WordSenseNormalizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户私有词书两阶段导入：用户释义只写私有学习卡，不进入公共来源库。
 */
@Service
public class BookImportService {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(15);
    private static final Duration RATE_LIMIT_TTL = Duration.ofMinutes(1);
    private static final int RATE_LIMIT_MAX = 10;
    private static final int PREVIEW_WORD_LIMIT = 6;

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public BookImportService(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析 JSON、CSV 或 TXT；允许只提供英文词头，候选释义在确认阶段生成。
     */
    public BookImportPreviewResponse preview(Long userId, MultipartFile file) {
        checkRateLimit(userId);
        if (file == null || file.isEmpty()) {
            throw new WordflipException("PARSE_ERROR", "未识别到有效单词");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new WordflipException("PAYLOAD_TOO_LARGE", "文件过大，上限 5MB");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException error) {
            throw new WordflipException("PARSE_ERROR", "读取文件失败");
        }
        ParsedImport parsed = parseContent(content);
        String token = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename() == null ? "导入词书" : file.getOriginalFilename();
        PreviewPayload payload = new PreviewPayload(userId, suggestName(fileName), parsed.words(), parsed.duplicates());
        try {
            redis.opsForValue().set(previewKey(token), objectMapper.writeValueAsString(payload), PREVIEW_TTL);
        } catch (JsonProcessingException error) {
            throw new WordflipException("INTERNAL_ERROR", "暂存预览失败");
        }
        return new BookImportPreviewResponse(
                token, payload.suggestedName(), payload.words().size(), payload.deduplicatedCount(),
                payload.words().stream().limit(PREVIEW_WORD_LIMIT).toList()
        );
    }

    /**
     * 创建私有词书和专属学习卡：中文输入优先，否则使用可靠公共候选；无候选则待补充。
     */
    @Transactional
    public BookImportConfirmResponse confirm(Long userId, String previewToken, String name) {
        checkRateLimit(userId);
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > 64) {
            throw new WordflipException("VALIDATION_ERROR", "词书名称无效");
        }
        PreviewPayload payload = readPreview(userId, previewToken);
        Integer duplicate = jdbc.queryForObject(
                "SELECT COUNT(*) FROM books WHERE owner_user_id=? AND name=? AND status<>'archived'",
                Integer.class, userId, trimmedName
        );
        if (duplicate != null && duplicate > 0) {
            throw new WordflipException("CONFLICT", "同名词书已存在");
        }

        String code = "user-" + UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO books(owner_user_id, code, name, source_type, visibility, status,
                                  declared_count, published_card_count, content_version)
                VALUES (?, ?, ?, 'imported', 'private', 'published', ?, 0, 'user-v1')
                """,
                userId, code, trimmedName, payload.words().size()
        );
        Long bookId = jdbc.queryForObject(
                "SELECT id FROM books WHERE owner_user_id=? AND code=?", Long.class, userId, code
        );
        int published = 0;
        int order = 0;
        for (WordSummary word : payload.words()) {
            Long lexemeId = upsertLexeme(word);
            Candidate candidate = chooseCandidate(lexemeId, word);
            String itemStatus = candidate == null ? "review_required" : "ready";
            jdbc.update(
                    """
                    INSERT INTO book_items(book_id, lexeme_id, sort_order, raw_headword, raw_meaning, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    bookId, lexemeId, order++, word.en(), blankToNull(word.cn()), itemStatus
            );
            Long itemId = jdbc.queryForObject(
                    "SELECT id FROM book_items WHERE book_id=? AND lexeme_id=?", Long.class, bookId, lexemeId
            );
            String cardStatus = candidate == null ? "review_required" : "published";
            jdbc.update(
                    """
                    INSERT INTO learning_cards(book_item_id, version, status, published_at, created_by, review_note)
                    VALUES (?, 1, ?, ?, 'user-import', ?)
                    """,
                    itemId, cardStatus,
                    "published".equals(cardStatus) ? Timestamp.from(Instant.now()) : null,
                    candidate == null ? "未找到可靠中文候选" : null
            );
            Long cardId = jdbc.queryForObject(
                    "SELECT id FROM learning_cards WHERE book_item_id=? AND version=1", Long.class, itemId
            );
            if (candidate != null) {
                jdbc.update(
                        """
                        INSERT INTO learning_card_senses(
                          card_id, source_sense_id, pos, cn, en_gloss, is_primary, quality,
                          sort_order, provenance_json
                        ) VALUES (?, ?, ?, ?, ?, TRUE, 'ok', 0, ?)
                        """,
                        cardId, candidate.sourceSenseId(), candidate.pos(), candidate.cn(),
                        candidate.enGloss(), candidate.provenanceJson()
                );
                published++;
            }
        }
        jdbc.update("UPDATE books SET published_card_count=? WHERE id=?", published, bookId);
        redis.delete(previewKey(previewToken));
        return new BookImportConfirmResponse(new BookListResponse.BookItem(
                bookId, trimmedName, "imported", published, payload.words().size(), false, true
        ));
    }

    /**
     * 未使用的私有词书直接删除；已有历史计划时仅归档，避免破坏学习历史。
     */
    @Transactional
    public void deleteImportedBook(Long userId, Long bookId) {
        List<String> sourceTypes = jdbc.query(
                "SELECT source_type FROM books WHERE id=? AND owner_user_id=?",
                (rs, row) -> rs.getString(1), bookId, userId
        );
        if (sourceTypes.isEmpty()) {
            Integer publicBook = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM books WHERE id=? AND owner_user_id IS NULL", Integer.class, bookId
            );
            if (publicBook != null && publicBook > 0) {
                throw new WordflipException("FORBIDDEN", "内置词书不可删除");
            }
            throw new WordflipException("NOT_FOUND", "词书不存在或不可访问");
        }
        Integer plans = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_learning_plans WHERE book_id=?", Integer.class, bookId
        );
        if (plans != null && plans > 0) {
            jdbc.update("UPDATE books SET status='archived' WHERE id=? AND owner_user_id=?", bookId, userId);
        } else {
            jdbc.update("DELETE FROM books WHERE id=? AND owner_user_id=?", bookId, userId);
        }
    }

    private Long upsertLexeme(WordSummary word) {
        jdbc.update(
                """
                INSERT INTO lexemes(word_key, headword, phonetic)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE headword=VALUES(headword),
                                        phonetic=COALESCE(lexemes.phonetic, VALUES(phonetic))
                """,
                word.wordKey(), word.en(), blankToNull(word.ph())
        );
        return jdbc.queryForObject(
                "SELECT id FROM lexemes WHERE language='en' AND word_key=?", Long.class, word.wordKey()
        );
    }

    private Candidate chooseCandidate(Long lexemeId, WordSummary word) {
        String cleaned = WordSenseNormalizer.cleanDisplayCn(word.cn());
        if (WordSenseNormalizer.hasHan(cleaned)) {
            String provenance = writeJson(Map.of("type", "user_import", "raw", word.cn()));
            return new Candidate(null, word.pos(), cleaned, word.enGloss(), provenance);
        }
        List<Candidate> candidates = jdbc.query(
                """
                SELECT ds.id, ds.pos, ds.cn, ds.en_gloss, cs.code, sr.version
                  FROM dictionary_senses ds
                  JOIN source_entries se ON se.id=ds.source_entry_id
                  JOIN source_revisions sr ON sr.id=se.revision_id
                  JOIN content_sources cs ON cs.id=sr.source_id
                 WHERE se.lexeme_id=? AND ds.quality='ok' AND ds.cn IS NOT NULL AND ds.cn<>''
                 ORDER BY CASE WHEN cs.code='ecdict' THEN 0 ELSE 1 END, ds.sort_order, ds.id
                 LIMIT 1
                """,
                (rs, row) -> new Candidate(
                        rs.getLong("id"), rs.getString("pos"), rs.getString("cn"),
                        rs.getString("en_gloss"), writeJson(Map.of(
                                "type", "public_candidate", "source", rs.getString("code"),
                                "revision", rs.getString("version")
                        ))
                ),
                lexemeId
        );
        return candidates.stream().findFirst().orElse(null);
    }

    private PreviewPayload readPreview(Long userId, String token) {
        String raw = redis.opsForValue().get(previewKey(token));
        if (raw == null) {
            throw new WordflipException("GONE", "预览已过期，请重新导入");
        }
        try {
            PreviewPayload payload = objectMapper.readValue(raw, PreviewPayload.class);
            if (!userId.equals(payload.userId())) {
                throw new WordflipException("FORBIDDEN", "无权使用该预览");
            }
            return payload;
        } catch (JsonProcessingException error) {
            throw new WordflipException("GONE", "预览已失效，请重新导入");
        }
    }

    private ParsedImport parseContent(String content) {
        if (content == null || content.isBlank()) {
            throw new WordflipException("PARSE_ERROR", "未识别到有效单词");
        }
        List<RawWord> raw = content.stripLeading().startsWith("[") || content.stripLeading().startsWith("{")
                ? parseJson(content) : parseLines(content);
        Map<String, WordSummary> unique = new LinkedHashMap<>();
        int duplicates = 0;
        for (RawWord item : raw) {
            if (item.en() == null || item.en().isBlank()) {
                continue;
            }
            String key = WordKeyUtil.normalize(item.en());
            if (key.isBlank()) {
                continue;
            }
            if (unique.containsKey(key)) {
                duplicates++;
                continue;
            }
            unique.put(key, new WordSummary(
                    key, item.en().trim(), blankToEmpty(item.cn()), item.pos(), item.ph()
            ));
        }
        if (unique.isEmpty()) {
            throw new WordflipException("PARSE_ERROR", "未识别到有效单词");
        }
        return new ParsedImport(List.copyOf(unique.values()), duplicates);
    }

    private List<RawWord> parseJson(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode array = root.isArray() ? root : root.path("words");
            if (!array.isArray()) {
                return List.of();
            }
            List<RawWord> values = new ArrayList<>();
            for (JsonNode node : array) {
                if (node.isTextual()) {
                    values.add(new RawWord(node.asText(), null, null, null));
                } else {
                    values.add(new RawWord(
                            text(node, "en"), text(node, "cn"), text(node, "pos"), text(node, "ph")
                    ));
                }
            }
            return values;
        } catch (JsonProcessingException error) {
            throw new WordflipException("PARSE_ERROR", "JSON 格式无效");
        }
    }

    private static List<RawWord> parseLines(String content) {
        List<RawWord> values = new ArrayList<>();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = splitLine(line);
            values.add(new RawWord(parts[0].trim(), parts.length > 1 ? parts[1].trim() : null, null, null));
        }
        return values;
    }

    private static String[] splitLine(String line) {
        for (String delimiter : List.of("\\t", "\\|", ";", " - ", ",")) {
            String[] parts = line.split(delimiter, 2);
            if (parts.length == 2) {
                return parts;
            }
        }
        return new String[]{line};
    }

    private void checkRateLimit(Long userId) {
        String key = "rl:import:" + userId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, RATE_LIMIT_TTL);
        }
        if (count != null && count > RATE_LIMIT_MAX) {
            throw new WordflipException("TOO_MANY_REQUESTS", "导入过于频繁，请稍后再试");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法生成内容追溯信息", error);
        }
    }

    private static String previewKey(String token) {
        return "import:preview:" + token;
    }

    private static String suggestName(String fileName) {
        String base = fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record RawWord(String en, String cn, String pos, String ph) {
    }

    private record ParsedImport(List<WordSummary> words, int duplicates) {
    }

    private record PreviewPayload(
            Long userId, String suggestedName, List<WordSummary> words, int deduplicatedCount
    ) {
    }

    private record Candidate(
            Long sourceSenseId, String pos, String cn, String enGloss, String provenanceJson
    ) {
    }
}
