package com.wordflip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.config.LexiconProperties;
import com.wordflip.domain.BookWord;
import com.wordflip.domain.DictExample;
import com.wordflip.domain.DictSense;
import com.wordflip.domain.DictSenseQuality;
import com.wordflip.domain.DictWord;
import com.wordflip.domain.Dictionary;
import com.wordflip.domain.DictionaryIds;
import com.wordflip.domain.DictionaryLocale;
import com.wordflip.domain.UserSettings;
import com.wordflip.domain.UserWordLexicon;
import com.wordflip.dto.study.WordDetailDto;
import com.wordflip.dto.word.ExampleDto;
import com.wordflip.dto.word.SenseDto;
import com.wordflip.dto.word.WordLookupResponse;
import com.wordflip.dto.word.WordSummary;
import com.wordflip.repository.BookWordRepository;
import com.wordflip.repository.DictExampleRepository;
import com.wordflip.repository.DictSenseRepository;
import com.wordflip.repository.DictWordRepository;
import com.wordflip.repository.DictionaryRepository;
import com.wordflip.repository.UserBookSelectionRepository;
import com.wordflip.repository.UserSettingsRepository;
import com.wordflip.repository.UserWordLexiconRepository;
import com.wordflip.util.WordSenseNormalizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 词义查询：按用户 activeDictId 读 dict_*；缺词同 locale 回退 curated；legacy 回退 lexicon。
 */
@Service
public class WordLookupService {

    private final UserWordLexiconRepository userWordLexiconRepository;
    private final UserBookSelectionRepository userBookSelectionRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final BookWordRepository bookWordRepository;
    private final DictWordRepository dictWordRepository;
    private final DictSenseRepository dictSenseRepository;
    private final DictExampleRepository dictExampleRepository;
    private final DictionaryRepository dictionaryRepository;
    private final LexiconProperties lexiconProperties;
    private final ObjectMapper objectMapper;

    public WordLookupService(
            UserWordLexiconRepository userWordLexiconRepository,
            UserBookSelectionRepository userBookSelectionRepository,
            UserSettingsRepository userSettingsRepository,
            BookWordRepository bookWordRepository,
            DictWordRepository dictWordRepository,
            DictSenseRepository dictSenseRepository,
            DictExampleRepository dictExampleRepository,
            DictionaryRepository dictionaryRepository,
            LexiconProperties lexiconProperties,
            ObjectMapper objectMapper
    ) {
        this.userWordLexiconRepository = userWordLexiconRepository;
        this.userBookSelectionRepository = userBookSelectionRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.bookWordRepository = bookWordRepository;
        this.dictWordRepository = dictWordRepository;
        this.dictSenseRepository = dictSenseRepository;
        this.dictExampleRepository = dictExampleRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.lexiconProperties = lexiconProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 按指定词典查询单个单词释义（详情抽屉临时切换，不影响全局 activeDictId）。
     *
     * @param userId  当前用户 ID
     * @param wordKey 单词键
     * @param dictId  词典 ID，null 时用用户 activeDictId
     * @return 该词典下的完整释义；词典不存在时回退到 curated
     */
    public WordLookupResponse lookupWord(Long userId, String wordKey, String dictId) {
        String effectiveDictId = (dictId != null && !dictId.isBlank()) ? dictId : resolveActiveDictId(userId);

        // 先尝试指定词典
        Map<String, WordSummary> result = resolveFromDict(effectiveDictId, List.of(wordKey));

        // 缺词回退：任何 locale 缺词时均回退 curated
        if (result.isEmpty() && !DictionaryIds.CURATED.equals(effectiveDictId)) {
            result = resolveFromDict(DictionaryIds.CURATED, List.of(wordKey));
            effectiveDictId = DictionaryIds.CURATED;
        }

        // 仍缺词回退 legacy
        if (result.isEmpty()) {
            result = resolveFromLegacy(userId, List.of(wordKey));
            effectiveDictId = DictionaryIds.CURATED; // legacy 视为 curated
        }

        WordSummary summary = result.get(wordKey);
        if (summary == null) {
            // 所有来源均无此词，返回空壳（理论上不应发生，因为入组词必存在）
            Dictionary dict = dictionaryRepository.findById(effectiveDictId).orElse(null);
            return new WordLookupResponse(
                    wordKey, wordKey, "", null, null, null,
                    List.of(), effectiveDictId,
                    dict != null ? dict.getName() : "WordFlip 精校",
                    dict != null ? dict.getLocale().name() : "zh"
            );
        }

        // 应用考义覆盖（仅当 exam_sense 属于当前查询词典时）
        if (userId != null && lexiconProperties.useDict()) {
            Map<String, WordSummary> overlayMap = new LinkedHashMap<>();
            overlayMap.put(wordKey, summary);
            applyExamSenseOverlay(userId, effectiveDictId, overlayMap);
            summary = overlayMap.get(wordKey);
        }

        Dictionary dict = dictionaryRepository.findById(effectiveDictId).orElse(null);
        String dictName = dict != null ? dict.getName() : "WordFlip 精校";
        String dictLocale = dict != null ? dict.getLocale().name() : "zh";

        return new WordLookupResponse(
                summary.wordKey(),
                summary.en(),
                summary.cn(),
                summary.pos(),
                summary.ph(),
                summary.enGloss(),
                summary.senses(),
                effectiveDictId,
                dictName,
                dictLocale
        );
    }

    /** 解析用户当前词典 ID；缺省精校。 */
    public String resolveActiveDictId(Long userId) {
        if (userId == null) {
            return DictionaryIds.CURATED;
        }
        return userSettingsRepository.findById(userId)
                .map(UserSettings::getActiveDictId)
                .filter(id -> id != null && !id.isBlank())
                .orElse(DictionaryIds.CURATED);
    }

    /** 当前词典 locale；未知时按 zh。 */
    public DictionaryLocale resolveActiveLocale(Long userId) {
        String dictId = resolveActiveDictId(userId);
        return dictionaryRepository.findById(dictId)
                .map(Dictionary::getLocale)
                .orElse(DictionaryLocale.zh);
    }

    /**
     * 批量解析 WordSummary：activeDict → 同 locale 回退 curated → legacy。
     */
    public Map<String, WordSummary> resolveWordSummaries(Long userId, List<String> wordKeys) {
        if (wordKeys.isEmpty()) {
            return Map.of();
        }
        String dictId = resolveActiveDictId(userId);
        DictionaryLocale locale = dictionaryRepository.findById(dictId)
                .map(Dictionary::getLocale)
                .orElse(DictionaryLocale.zh);

        Map<String, WordSummary> result = new LinkedHashMap<>();
        if (lexiconProperties.useDict()) {
            result.putAll(resolveFromDict(dictId, wordKeys));
            List<String> missing = wordKeys.stream().filter(k -> !result.containsKey(k)).toList();
            // 回退：任何 locale 缺词时均回退 curated（V23 WordNet 仅 10 词种子，全量灌数前须兼容）
            if (!missing.isEmpty() && !DictionaryIds.CURATED.equals(dictId)) {
                result.putAll(resolveFromDict(DictionaryIds.CURATED, missing));
            }
        }
        List<String> stillMissing = wordKeys.stream().filter(key -> !result.containsKey(key)).toList();
        if (!stillMissing.isEmpty()) {
            result.putAll(resolveFromLegacy(userId, stillMissing));
        }
        Map<String, WordSummary> ordered = new LinkedHashMap<>();
        for (String key : wordKeys) {
            WordSummary summary = result.get(key);
            if (summary != null) {
                ordered.put(key, summary);
            }
        }
        if (lexiconProperties.useDict() && userId != null) {
            applyExamSenseOverlay(userId, dictId, ordered);
        }
        return ordered;
    }

    private Map<String, WordSummary> resolveFromDict(String dictId, List<String> wordKeys) {
        Map<String, WordSummary> result = new LinkedHashMap<>();
        List<DictWord> heads = dictWordRepository.findByDictIdAndWordKeyIn(dictId, wordKeys);
        if (heads.isEmpty()) {
            return result;
        }
        Map<String, DictWord> headByKey = heads.stream()
                .collect(Collectors.toMap(DictWord::getWordKey, w -> w, (a, b) -> a));
        List<DictSense> allSenses = dictSenseRepository.findByDictIdAndWordKeyInOrdered(
                dictId, headByKey.keySet());
        Map<String, List<DictSense>> sensesByKey = allSenses.stream()
                .collect(Collectors.groupingBy(DictSense::getWordKey, LinkedHashMap::new, Collectors.toList()));

        List<Long> senseIds = allSenses.stream().map(DictSense::getId).toList();
        Map<Long, List<DictExample>> examplesBySense = senseIds.isEmpty()
                ? Map.of()
                : dictExampleRepository.findBySenseIdInOrderBySenseIdAscSortOrderAsc(senseIds).stream()
                .collect(Collectors.groupingBy(DictExample::getSenseId, LinkedHashMap::new, Collectors.toList()));

        for (String key : wordKeys) {
            DictWord head = headByKey.get(key);
            if (head == null) {
                continue;
            }
            List<DictSense> senses = sensesByKey.getOrDefault(key, List.of());
            DictSense primary = senses.stream()
                    .filter(s -> s.isPrimary() && s.getQuality() == DictSenseQuality.ok)
                    .filter(this::senseHasUsableGloss)
                    .findFirst()
                    .orElse(null);
            if (primary == null) {
                continue;
            }
            List<SenseDto> senseDtos = new ArrayList<>(senses.size());
            for (DictSense s : senses) {
                List<ExampleDto> examples = examplesBySense.getOrDefault(s.getId(), List.of()).stream()
                        .map(e -> new ExampleDto(e.getEn(), e.getCn(), e.getSortOrder()))
                        .toList();
                senseDtos.add(new SenseDto(
                        s.getId(),
                        s.getPos(),
                        s.getCn(),
                        s.getEnGloss(),
                        s.isPrimary(),
                        s.getQuality().name(),
                        s.getSortOrder(),
                        examples
                ));
            }
            result.put(key, new WordSummary(
                    head.getWordKey(),
                    head.getEn(),
                    primary.getCn(),
                    primary.getPos(),
                    head.getPh(),
                    primary.getEnGloss(),
                    senseDtos
            ));
        }
        return result;
    }

    private boolean senseHasUsableGloss(DictSense s) {
        return (s.getCn() != null && WordSenseNormalizer.hasHan(s.getCn()))
                || (s.getEnGloss() != null && !s.getEnGloss().isBlank());
    }

    /**
     * 考义覆盖：仅当 exam_sense 属于当前词典时生效。
     */
    private void applyExamSenseOverlay(Long userId, String activeDictId, Map<String, WordSummary> result) {
        if (result.isEmpty()) {
            return;
        }
        List<Long> selectedBookIds = userBookSelectionRepository.findBookIdsByUserId(userId);
        if (selectedBookIds.isEmpty()) {
            return;
        }
        List<BookWord> bookWords = bookWordRepository.findByBookIdsAndWordKeys(
                selectedBookIds, result.keySet());
        Map<String, Long> examIdByKey = new LinkedHashMap<>();
        for (BookWord bookWord : bookWords) {
            if (bookWord.getExamSenseId() != null) {
                examIdByKey.putIfAbsent(bookWord.getWordKey(), bookWord.getExamSenseId());
            }
        }
        if (examIdByKey.isEmpty()) {
            return;
        }
        Map<Long, DictSense> senseById = dictSenseRepository.findByIdIn(examIdByKey.values()).stream()
                .collect(Collectors.toMap(DictSense::getId, s -> s, (a, b) -> a));

        for (Map.Entry<String, Long> entry : examIdByKey.entrySet()) {
            WordSummary current = result.get(entry.getKey());
            DictSense exam = senseById.get(entry.getValue());
            if (current == null || exam == null || exam.getQuality() != DictSenseQuality.ok) {
                continue;
            }
            if (!activeDictId.equals(exam.getDictId())) {
                continue;
            }
            if (!senseHasUsableGloss(exam)) {
                continue;
            }
            List<SenseDto> remapped = new ArrayList<>();
            boolean found = false;
            for (SenseDto sense : current.senses()) {
                boolean isExam = sense.id() != null && sense.id().equals(exam.getId());
                if (isExam) {
                    found = true;
                }
                remapped.add(new SenseDto(
                        sense.id(),
                        sense.pos(),
                        sense.cn(),
                        sense.enGloss(),
                        isExam,
                        sense.quality(),
                        sense.sortOrder(),
                        sense.examples()
                ));
            }
            if (!found) {
                continue;
            }
            result.put(entry.getKey(), new WordSummary(
                    current.wordKey(),
                    current.en(),
                    exam.getCn(),
                    exam.getPos(),
                    current.ph(),
                    exam.getEnGloss(),
                    remapped
            ));
        }
    }

    private Map<String, WordSummary> resolveFromLegacy(Long userId, List<String> wordKeys) {
        Map<String, WordSummary> result = new LinkedHashMap<>();
        for (UserWordLexicon lexicon : userWordLexiconRepository.findByUserIdAndWordKeyIn(userId, wordKeys)) {
            result.put(
                    lexicon.getWordKey(),
                    WordSenseNormalizer.normalizeSummary(new WordSummary(
                            lexicon.getWordKey(),
                            lexicon.getEn(),
                            lexicon.getCn(),
                            lexicon.getPos(),
                            lexicon.getPh()
                    ))
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
                    WordSenseNormalizer.normalizeSummary(new WordSummary(
                            bookWord.getWordKey(),
                            bookWord.getEn(),
                            bookWord.getCn(),
                            bookWord.getPos(),
                            bookWord.getPh()
                    ))
            );
        }
        return result;
    }

    public WordDetailDto resolveDetail(Long userId, String wordKey, String detailJson, String fallbackCn) {
        if (lexiconProperties.useDict()) {
            WordSummary fromDict = resolveWordSummaries(userId, List.of(wordKey)).get(wordKey);
            if (fromDict != null) {
                List<String> examples = fromDict.senses().stream()
                        .flatMap(s -> s.examples().stream())
                        .map(e -> e.cn() != null && !e.cn().isBlank() ? e.en() + " — " + e.cn() : e.en())
                        .limit(5)
                        .toList();
                String meaning = WordSenseNormalizer.displayPrompt(fromDict);
                if (meaning.isBlank()) {
                    meaning = fallbackCn;
                }
                return new WordDetailDto(meaning, examples, null);
            }
        }
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

    public Map<String, WordDetailDto> resolveDetails(Long userId, List<String> wordKeys) {
        Map<String, WordDetailDto> result = new LinkedHashMap<>();
        if (wordKeys.isEmpty()) {
            return result;
        }
        Map<String, WordSummary> summaries = resolveWordSummaries(userId, wordKeys);
        for (Map.Entry<String, WordSummary> e : summaries.entrySet()) {
            WordDetailDto detail = resolveDetail(userId, e.getKey(), null, e.getValue().cn());
            if (detail != null) {
                result.put(e.getKey(), detail);
            }
        }
        List<String> missing = wordKeys.stream().filter(key -> !result.containsKey(key)).toList();
        if (missing.isEmpty()) {
            return result;
        }
        for (UserWordLexicon lexicon : userWordLexiconRepository.findByUserIdAndWordKeyIn(userId, missing)) {
            WordDetailDto detail = resolveDetail(userId, lexicon.getWordKey(), lexicon.getDetailJson(), lexicon.getCn());
            if (detail != null) {
                result.put(lexicon.getWordKey(), detail);
            }
        }
        List<String> stillMissing = missing.stream().filter(key -> !result.containsKey(key)).toList();
        if (stillMissing.isEmpty()) {
            return result;
        }
        List<Long> selectedBookIds = userBookSelectionRepository.findBookIdsByUserId(userId);
        if (selectedBookIds.isEmpty()) {
            return result;
        }
        for (BookWord bookWord : bookWordRepository.findByBookIdsAndWordKeys(selectedBookIds, stillMissing)) {
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
