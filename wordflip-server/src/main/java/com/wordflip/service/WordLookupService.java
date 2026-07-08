package com.wordflip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.domain.BookWord;
import com.wordflip.domain.UserWordLexicon;
import com.wordflip.dto.study.WordDetailDto;
import com.wordflip.dto.word.WordSummary;
import com.wordflip.repository.BookWordRepository;
import com.wordflip.repository.UserBookSelectionRepository;
import com.wordflip.repository.UserWordLexiconRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 词义查询：优先 user_word_lexicon，回退 book_words（复用 GroupService 逻辑供 Study/Today 共用）。
 */
@Service
public class WordLookupService {

    private final UserWordLexiconRepository userWordLexiconRepository;
    private final UserBookSelectionRepository userBookSelectionRepository;
    private final BookWordRepository bookWordRepository;
    private final ObjectMapper objectMapper;

    public WordLookupService(
            UserWordLexiconRepository userWordLexiconRepository,
            UserBookSelectionRepository userBookSelectionRepository,
            BookWordRepository bookWordRepository,
            ObjectMapper objectMapper
    ) {
        this.userWordLexiconRepository = userWordLexiconRepository;
        this.userBookSelectionRepository = userBookSelectionRepository;
        this.bookWordRepository = bookWordRepository;
        this.objectMapper = objectMapper;
    }

    /** 批量解析 WordSummary，保持 wordKeys 顺序 */
    public Map<String, WordSummary> resolveWordSummaries(Long userId, List<String> wordKeys) {
        if (wordKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, WordSummary> result = new LinkedHashMap<>();
        for (UserWordLexicon lexicon : userWordLexiconRepository.findByUserIdAndWordKeyIn(userId, wordKeys)) {
            result.put(
                    lexicon.getWordKey(),
                    new WordSummary(
                            lexicon.getWordKey(),
                            lexicon.getEn(),
                            lexicon.getCn(),
                            lexicon.getPos(),
                            lexicon.getPh()
                    )
            );
        }
        List<String> missing = wordKeys.stream().filter(key -> !result.containsKey(key)).toList();
        if (missing.isEmpty()) {
            return result;
        }
        List<Long> selectedBookIds = userBookSelectionRepository.findBookIdsByUserId(userId);
        if (selectedBookIds.isEmpty()) {
            return result;
        }
        for (BookWord bookWord : bookWordRepository.findByBookIdsAndWordKeys(selectedBookIds, missing)) {
            result.putIfAbsent(
                    bookWord.getWordKey(),
                    new WordSummary(
                            bookWord.getWordKey(),
                            bookWord.getEn(),
                            bookWord.getCn(),
                            bookWord.getPos(),
                            bookWord.getPh()
                    )
            );
        }
        return result;
    }

    /** 解析 detail_json → WordDetailDto；无 JSON 时用 cn 作 meaning */
    public WordDetailDto resolveDetail(Long userId, String wordKey, String detailJson, String fallbackCn) {
        if (detailJson != null && !detailJson.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(detailJson);
                String meaning = textOrNull(node.get("meaning"));
                if (meaning == null) {
                    meaning = fallbackCn;
                }
                List<String> examples = parseExamples(node.get("examples"));
                String etymology = textOrNull(node.get("etymology"));
                return new WordDetailDto(meaning, examples, etymology);
            } catch (Exception ignored) {
                // 解析失败降级为 cn
            }
        }
        if (fallbackCn != null && !fallbackCn.isBlank()) {
            return new WordDetailDto(fallbackCn, List.of(), null);
        }
        return null;
    }

    /** 批量拉取 detail_json 来源（lexicon 优先，回退 book_words） */
    public Map<String, WordDetailDto> resolveDetails(Long userId, List<String> wordKeys) {
        Map<String, WordDetailDto> result = new LinkedHashMap<>();
        if (wordKeys.isEmpty()) {
            return result;
        }
        for (UserWordLexicon lexicon : userWordLexiconRepository.findByUserIdAndWordKeyIn(userId, wordKeys)) {
            WordDetailDto detail = resolveDetail(userId, lexicon.getWordKey(), lexicon.getDetailJson(), lexicon.getCn());
            if (detail != null) {
                result.put(lexicon.getWordKey(), detail);
            }
        }
        List<String> missing = wordKeys.stream().filter(key -> !result.containsKey(key)).toList();
        if (missing.isEmpty()) {
            return result;
        }
        List<Long> selectedBookIds = userBookSelectionRepository.findBookIdsByUserId(userId);
        if (selectedBookIds.isEmpty()) {
            return result;
        }
        for (BookWord bookWord : bookWordRepository.findByBookIdsAndWordKeys(selectedBookIds, missing)) {
            WordDetailDto detail = resolveDetail(
                    userId,
                    bookWord.getWordKey(),
                    bookWord.getDetailJson(),
                    bookWord.getCn()
            );
            if (detail != null) {
                result.putIfAbsent(bookWord.getWordKey(), detail);
            }
        }
        return result;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private List<String> parseExamples(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(
                node,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );
    }
}
