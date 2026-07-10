package com.wordflip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.domain.Book;
import com.wordflip.domain.BookSource;
import com.wordflip.domain.BookWord;
import com.wordflip.domain.UserBookSelection;
import com.wordflip.domain.UserWordLexicon;
import com.wordflip.dto.book.BookImportConfirmResponse;
import com.wordflip.dto.book.BookImportPreviewResponse;
import com.wordflip.dto.book.BookListResponse;
import com.wordflip.dto.word.WordSummary;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.BookRepository;
import com.wordflip.repository.BookWordRepository;
import com.wordflip.repository.UserBookSelectionRepository;
import com.wordflip.repository.UserWordLexiconRepository;
import com.wordflip.util.WordSenseNormalizer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 词书导入两阶段：preview（Redis 15min）→ confirm 入库；不自动 append 分组。
 */
@Service
public class BookImportService {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(15);
    private static final Duration RATE_LIMIT_TTL = Duration.ofMinutes(1);
    private static final int RATE_LIMIT_MAX = 10;
    private static final int PREVIEW_WORD_LIMIT = 6;

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile(
            "\\{\\s*\"en\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"cn\"\\s*:\\s*\"([^\"]*)\"\\s*\\}"
    );

    private final BookRepository bookRepository;
    private final BookWordRepository bookWordRepository;
    private final UserBookSelectionRepository userBookSelectionRepository;
    private final UserWordLexiconRepository userWordLexiconRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public BookImportService(
            BookRepository bookRepository,
            BookWordRepository bookWordRepository,
            UserBookSelectionRepository userBookSelectionRepository,
            UserWordLexiconRepository userWordLexiconRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.bookRepository = bookRepository;
        this.bookWordRepository = bookWordRepository;
        this.userBookSelectionRepository = userBookSelectionRepository;
        this.userWordLexiconRepository = userWordLexiconRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /books/import/preview：解析文件并暂存 Redis。
     */
    public BookImportPreviewResponse preview(Long userId, MultipartFile file) {
        checkRateLimit(userId);
        if (file == null || file.isEmpty()) {
            throw new WordflipException("PARSE_ERROR", "未识别到有效单词");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new WordflipException("PAYLOAD_TOO_LARGE", "文件过大，上限 5MB");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "import.txt";
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new WordflipException("PARSE_ERROR", "读取文件失败");
        }
        if (content.isEmpty()) {
            throw new WordflipException("PARSE_ERROR", "未识别到有效单词");
        }

        String suggestedName = suggestName(originalName);
        ParsedImport parsed = parseContent(content);
        String token = UUID.randomUUID().toString();
        PreviewPayload payload = new PreviewPayload(userId, suggestedName, parsed.words(), parsed.deduplicatedCount());
        try {
            redisTemplate.opsForValue().set(previewKey(token), objectMapper.writeValueAsString(payload), PREVIEW_TTL);
        } catch (JsonProcessingException e) {
            throw new WordflipException("INTERNAL_ERROR", "暂存预览失败");
        }

        List<WordSummary> previewWords = parsed.words().stream().limit(PREVIEW_WORD_LIMIT).toList();
        return new BookImportPreviewResponse(
                token,
                suggestedName,
                parsed.words().size(),
                parsed.deduplicatedCount(),
                previewWords
        );
    }

    /**
     * POST /books/import：确认入库并自动勾选；不调用 appendGroups。
     */
    @Transactional
    public BookImportConfirmResponse confirm(Long userId, String previewToken, String name) {
        checkRateLimit(userId);
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > 64) {
            throw new WordflipException("VALIDATION_ERROR", "词书名称无效");
        }

        String raw = redisTemplate.opsForValue().get(previewKey(previewToken));
        if (raw == null) {
            // 区分无效与过期：token 格式合法但无值 → 410；否则 404
            throw new WordflipException("GONE", "预览已过期，请重新导入");
        }

        PreviewPayload payload;
        try {
            payload = objectMapper.readValue(raw, PreviewPayload.class);
        } catch (JsonProcessingException e) {
            redisTemplate.delete(previewKey(previewToken));
            throw new WordflipException("GONE", "预览已过期，请重新导入");
        }
        if (!userId.equals(payload.userId())) {
            throw new WordflipException("FORBIDDEN", "无权使用该预览");
        }
        if (payload.words() == null || payload.words().isEmpty()) {
            throw new WordflipException("PARSE_ERROR", "未识别到有效单词");
        }
        if (bookRepository.existsByUserIdAndName(userId, trimmedName)) {
            throw new WordflipException("CONFLICT", "同名词书已存在");
        }

        Book book = new Book();
        book.setSource(BookSource.imported);
        book.setUserId(userId);
        book.setName(trimmedName);
        book.setDeclaredCount(null);
        book.setWordCount(payload.words().size());
        book.setCreatedAt(Instant.now());
        book.setUpdatedAt(Instant.now());
        book = bookRepository.save(book);

        List<BookWord> entities = new ArrayList<>(payload.words().size());
        int order = 0;
        for (WordSummary word : payload.words()) {
            BookWord bw = new BookWord();
            bw.setBookId(book.getId());
            bw.setWordKey(word.wordKey());
            bw.setEn(word.en());
            bw.setCn(word.cn());
            bw.setPos(word.pos());
            bw.setPh(word.ph());
            bw.setSortOrder(order++);
            bw.setCreatedAt(Instant.now());
            entities.add(bw);
        }
        bookWordRepository.saveAll(entities);

        // 自动勾选（REQ-BOOK-8）；不触发分组追加
        if (!userBookSelectionRepository.existsByIdUserIdAndIdBookId(userId, book.getId())) {
            userBookSelectionRepository.save(new UserBookSelection(userId, book.getId()));
        }
        upsertLexiconForImport(userId, book.getId(), payload.words());

        redisTemplate.delete(previewKey(previewToken));
        return new BookImportConfirmResponse(BookListResponse.BookItem.from(book, true));
    }

    /**
     * DELETE /books/{bookId}：仅 imported；已入组词保留。
     */
    @Transactional
    public void deleteImportedBook(Long userId, Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "词书不存在"));
        if (book.getSource() == BookSource.builtin) {
            throw new WordflipException("FORBIDDEN", "内置词书不可删除");
        }
        if (!userId.equals(book.getUserId())) {
            throw new WordflipException("NOT_FOUND", "词书不存在或不可访问");
        }
        // selection / book_words 随 books CASCADE 删除；group_words 无 FK，保留
        bookRepository.delete(book);
    }

    private void upsertLexiconForImport(Long userId, Long bookId, List<WordSummary> words) {
        List<String> keys = words.stream().map(WordSummary::wordKey).toList();
        Set<String> existing = userWordLexiconRepository.findExistingWordKeys(userId, keys);
        Instant now = Instant.now();
        for (WordSummary word : words) {
            if (existing.contains(word.wordKey())) {
                continue;
            }
            UserWordLexicon lexicon = new UserWordLexicon();
            lexicon.setUserId(userId);
            lexicon.setWordKey(word.wordKey());
            lexicon.setEn(word.en());
            lexicon.setCn(word.cn());
            lexicon.setPos(word.pos());
            lexicon.setPh(word.ph());
            lexicon.setSourceBookId(bookId);
            lexicon.setUpdatedAt(now);
            userWordLexiconRepository.save(lexicon);
        }
    }

    /** 导入限流：每用户每分钟最多 RATE_LIMIT_MAX 次 preview/confirm */
    private void checkRateLimit(Long userId) {
        String key = "rl:import:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RATE_LIMIT_TTL);
        }
        if (count != null && count > RATE_LIMIT_MAX) {
            throw new WordflipException("TOO_MANY_REQUESTS", "导入过于频繁，请稍后再试");
        }
    }

    private static String previewKey(String token) {
        return "import:preview:" + token;
    }

    private static String suggestName(String fileName) {
        String base = fileName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base.isBlank() ? "导入词书" : base;
    }

    private ParsedImport parseContent(String content) {
        List<EnCn> raw;
        String trimmed = content.trim();
        if (trimmed.startsWith("{")) {
            raw = parseJsonObject(trimmed);
        } else if (trimmed.startsWith("[")) {
            raw = parseJsonArray(trimmed);
        } else {
            raw = parseDelimitedLines(trimmed);
        }
        if (raw.isEmpty()) {
            throw new WordflipException("PARSE_ERROR", "未识别到有效单词");
        }

        Map<String, WordSummary> unique = new LinkedHashMap<>();
        int dedup = 0;
        for (EnCn pair : raw) {
            String en = pair.en() == null ? "" : pair.en().trim();
            String cn = pair.cn() == null ? "" : pair.cn().trim();
            if (en.isEmpty() || cn.isEmpty()) {
                continue;
            }
            String wordKey = en.toLowerCase();
            if (unique.containsKey(wordKey)) {
                dedup++;
                continue;
            }
            unique.put(wordKey, new WordSummary(
                    wordKey,
                    en,
                    WordSenseNormalizer.cleanDisplayCn(cn),
                    pair.pos(),
                    pair.ph()
            ));
        }
        if (unique.isEmpty()) {
            throw new WordflipException("PARSE_ERROR", "未识别到有效单词");
        }
        return new ParsedImport(List.copyOf(unique.values()), dedup);
    }

    private List<EnCn> parseJsonObject(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root.has("words") && root.get("words").isArray()) {
                return parseJsonWordNodes(root.get("words"));
            }
            // 回退：当作数组文本再试
            return parseJsonArray(content);
        } catch (JsonProcessingException e) {
            return parseJsonArray(content);
        }
    }

    private List<EnCn> parseJsonArray(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root.isArray()) {
                return parseJsonWordNodes(root);
            }
        } catch (JsonProcessingException ignored) {
            // 宽松正则回退
        }
        List<EnCn> fallback = new ArrayList<>();
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(content);
        while (matcher.find()) {
            fallback.add(new EnCn(matcher.group(1), matcher.group(2), null, null));
        }
        return fallback;
    }

    private List<EnCn> parseJsonWordNodes(JsonNode array) {
        List<EnCn> result = new ArrayList<>();
        for (JsonNode node : array) {
            String en = textOrNull(node, "en");
            String cn = textOrNull(node, "cn");
            if (en == null || cn == null) {
                continue;
            }
            result.add(new EnCn(en, cn, textOrNull(node, "pos"), textOrNull(node, "ph")));
        }
        return result;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String value = child.asText();
        return value == null || value.isBlank() ? null : value;
    }

    /** CSV/TXT：逗号/分号/竖线/Tab/` - ` 分隔；# 注释 */
    private static List<EnCn> parseDelimitedLines(String content) {
        List<EnCn> result = new ArrayList<>();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = splitLine(line);
            if (parts == null || parts.length < 2) {
                continue;
            }
            result.add(new EnCn(parts[0].trim(), parts[1].trim(), null, null));
        }
        return result;
    }

    private static String[] splitLine(String line) {
        if (line.contains("\t")) {
            return line.split("\t", 2);
        }
        if (line.contains("|")) {
            return line.split("\\|", 2);
        }
        if (line.contains(";")) {
            return line.split(";", 2);
        }
        if (line.contains(" - ")) {
            return line.split(" - ", 2);
        }
        if (line.contains(",")) {
            return line.split(",", 2);
        }
        return null;
    }

    private record EnCn(String en, String cn, String pos, String ph) {
    }

    private record ParsedImport(List<WordSummary> words, int deduplicatedCount) {
    }

    /** Redis 暂存载荷 */
    private record PreviewPayload(Long userId, String suggestedName, List<WordSummary> words, int deduplicatedCount) {
    }
}
