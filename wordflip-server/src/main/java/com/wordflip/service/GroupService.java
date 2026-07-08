package com.wordflip.service;

import com.wordflip.domain.BookWord;
import com.wordflip.domain.GroupSource;
import com.wordflip.domain.GroupStatus;
import com.wordflip.domain.GroupWord;
import com.wordflip.domain.MasteryLevel;
import com.wordflip.domain.StudyGroup;
import com.wordflip.domain.UserSettings;
import com.wordflip.domain.UserWordLexicon;
import com.wordflip.domain.WordMastery;
import com.wordflip.dto.common.PageMeta;
import com.wordflip.dto.group.CreateCustomGroupRequest;
import com.wordflip.dto.group.GroupDetail;
import com.wordflip.dto.group.GroupListResponse;
import com.wordflip.dto.group.GroupStats;
import com.wordflip.dto.group.GroupWordsResponse;
import com.wordflip.dto.settings.AppendedGroups;
import com.wordflip.dto.word.MasterySnapshot;
import com.wordflip.dto.word.UnassignedWordsResponse;
import com.wordflip.dto.word.WordSummary;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.BookWordRepository;
import com.wordflip.repository.GroupRepository;
import com.wordflip.repository.GroupWordRepository;
import com.wordflip.repository.UserBookSelectionRepository;
import com.wordflip.repository.UserSettingsRepository;
import com.wordflip.repository.UserWordLexiconRepository;
import com.wordflip.repository.WordMasteryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分组业务：增量 append（PUT /settings）与读 API（GET /groups、custom 创建）。
 */
@Service
public class GroupService {

    private static final int UNASSIGNED_ALL_MAX = 5000;

    private final UserBookSelectionRepository userBookSelectionRepository;
    private final BookWordRepository bookWordRepository;
    private final GroupWordRepository groupWordRepository;
    private final GroupRepository groupRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserWordLexiconRepository userWordLexiconRepository;
    private final WordMasteryRepository wordMasteryRepository;
    private final BookService bookService;

    public GroupService(
            UserBookSelectionRepository userBookSelectionRepository,
            BookWordRepository bookWordRepository,
            GroupWordRepository groupWordRepository,
            GroupRepository groupRepository,
            UserSettingsRepository userSettingsRepository,
            UserWordLexiconRepository userWordLexiconRepository,
            WordMasteryRepository wordMasteryRepository,
            BookService bookService
    ) {
        this.userBookSelectionRepository = userBookSelectionRepository;
        this.bookWordRepository = bookWordRepository;
        this.groupWordRepository = groupWordRepository;
        this.groupRepository = groupRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.userWordLexiconRepository = userWordLexiconRepository;
        this.wordMasteryRepository = wordMasteryRepository;
        this.bookService = bookService;
    }

    /** GET /groups：按 source 过滤、createdAt/name 排序 */
    @Transactional(readOnly = true)
    public GroupListResponse listGroups(Long userId, GroupSource source, String sort) {
        List<StudyGroup> groups = queryGroups(userId, source, sort);
        List<GroupDetail> details = groups.stream()
                .map(group -> toGroupDetail(userId, group))
                .toList();
        return new GroupListResponse(details);
    }

    /** GET /groups/{groupId} */
    @Transactional(readOnly = true)
    public GroupDetail getGroup(Long userId, Long groupId) {
        StudyGroup group = requireOwnedGroup(userId, groupId);
        return toGroupDetail(userId, group);
    }

    /** GET /groups/{groupId}/words：分页 + 只读掌握度占位 */
    @Transactional(readOnly = true)
    public GroupWordsResponse listGroupWords(Long userId, Long groupId, int page, int size) {
        requireOwnedGroup(userId, groupId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<GroupWord> wordPage = groupWordRepository.findByGroupIdOrderBySortOrderAsc(
                groupId,
                PageRequest.of(safePage, safeSize)
        );
        List<String> wordKeys = wordPage.getContent().stream().map(GroupWord::getWordKey).toList();
        Map<String, WordSummary> summaries = resolveWordSummaries(userId, wordKeys);
        Map<String, WordMastery> masteryByKey = loadMasteryMap(userId, wordKeys);

        List<GroupWordsResponse.GroupWordItem> items = wordKeys.stream()
                .map(key -> {
                    WordSummary summary = summaries.get(key);
                    if (summary == null) {
                        summary = new WordSummary(key, key, "", null, null);
                    }
                    MasterySnapshot mastery = masteryByKey.containsKey(key)
                            ? MasterySnapshot.from(masteryByKey.get(key))
                            : MasterySnapshot.unlearnedDefault();
                    return GroupWordsResponse.GroupWordItem.from(summary, mastery);
                })
                .toList();

        PageMeta meta = PageMeta.of(safePage, safeSize, wordPage.getTotalElements());
        return GroupWordsResponse.of(meta, items);
    }

    /** GET /words/unassigned：未入组词池 */
    @Transactional(readOnly = true)
    public UnassignedWordsResponse listUnassignedWords(
            Long userId,
            boolean all,
            String query,
            int page,
            int size
    ) {
        List<String> keys = listUnassignedWordKeys(userId);
        if (query != null && !query.isBlank()) {
            String q = query.trim().toLowerCase(Locale.ROOT);
            Map<String, WordSummary> summaryMap = resolveWordSummaries(userId, keys);
            keys = keys.stream()
                    .filter(key -> matchesQuery(summaryMap.get(key), key, q))
                    .toList();
        }

        if (all) {
            List<String> limited = keys.stream().limit(UNASSIGNED_ALL_MAX).toList();
            List<WordSummary> words = toWordSummaries(userId, limited);
            PageMeta meta = PageMeta.of(0, words.size(), words.size());
            return UnassignedWordsResponse.of(meta, words);
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int from = Math.min(safePage * safeSize, keys.size());
        int to = Math.min(from + safeSize, keys.size());
        List<String> pageKeys = keys.subList(from, to);
        List<WordSummary> words = toWordSummaries(userId, pageKeys);
        PageMeta meta = PageMeta.of(safePage, safeSize, keys.size());
        return UnassignedWordsResponse.of(meta, words);
    }

    /** POST /groups/custom：从未入组池创建 custom 分组 */
    @Transactional
    public GroupDetail createCustomGroup(Long userId, CreateCustomGroupRequest request) {
        List<String> normalized = request.getWordKeys().stream()
                .map(key -> key == null ? "" : key.trim().toLowerCase(Locale.ROOT))
                .filter(key -> !key.isEmpty())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new WordflipException("VALIDATION_ERROR", "wordKeys 不能为空");
        }

        Set<String> assigned = groupWordRepository.findWordKeysByUserId(userId);
        for (String key : normalized) {
            if (assigned.contains(key)) {
                throw new WordflipException("CONFLICT", "单词已存在于其他分组: " + key);
            }
        }

        Set<String> unassigned = new HashSet<>(listUnassignedWordKeys(userId));
        List<String> validKeys = normalized.stream().filter(unassigned::contains).toList();
        if (validKeys.isEmpty()) {
            throw new WordflipException("VALIDATION_ERROR", "wordKeys 均不在未入组词池");
        }
        if (validKeys.size() != normalized.size()) {
            throw new WordflipException("CONFLICT", "部分 wordKey 不在未入组词池或已入组");
        }

        List<Long> selectedBookIds = userBookSelectionRepository.findBookIdsByUserId(userId);
        bookService.upsertLexiconForNewWords(userId, validKeys, selectedBookIds);

        int customCount = groupRepository.countByUserIdAndSource(userId, GroupSource.custom);
        int maxSortOrder = groupRepository.findMaxSortOrderByUserId(userId).orElse(0);
        String groupName = request.getName() != null && !request.getName().isBlank()
                ? request.getName().trim()
                : "自定义分组 " + (customCount + 1);

        StudyGroup group = new StudyGroup();
        group.setUserId(userId);
        group.setName(groupName);
        group.setSource(GroupSource.custom);
        group.setStatus(GroupStatus.not_started);
        group.setSortOrder(maxSortOrder + 1);

        try {
            group = groupRepository.save(group);
            for (int i = 0; i < validKeys.size(); i++) {
                GroupWord groupWord = new GroupWord();
                groupWord.setUserId(userId);
                groupWord.setGroupId(group.getId());
                groupWord.setWordKey(validKeys.get(i));
                groupWord.setSortOrder(i);
                groupWordRepository.save(groupWord);
            }
        } catch (DataIntegrityViolationException ex) {
            throw new WordflipException("CONFLICT", "单词已存在于其他分组");
        }

        return toGroupDetail(userId, group);
    }

    /**
     * 增量追加分组（api-modules §2.1）：
     * delta = 已勾选词书 wordKey 去重 − 已在 group_words 中的 wordKey。
     */
    @Transactional
    public AppendedGroups appendGroupsForNewWords(Long userId) {
        List<Long> selectedBookIds = userBookSelectionRepository.findBookIdsByUserId(userId);
        if (selectedBookIds.isEmpty()) {
            return AppendedGroups.empty();
        }

        Set<String> selectedWordKeys = new HashSet<>(bookWordRepository.findDistinctWordKeysByBookIds(selectedBookIds));
        Set<String> assignedWordKeys = groupWordRepository.findWordKeysByUserId(userId);

        List<String> delta = selectedWordKeys.stream()
                .filter(key -> !assignedWordKeys.contains(key))
                .sorted()
                .collect(Collectors.toList());

        if (delta.isEmpty()) {
            return AppendedGroups.empty();
        }

        int groupSize = userSettingsRepository.findById(userId)
                .map(UserSettings::getGroupSize)
                .orElse(20);

        bookService.upsertLexiconForNewWords(userId, delta, selectedBookIds);

        int existingAutoCount = groupRepository.countByUserIdAndSource(userId, GroupSource.auto);
        int maxSortOrder = groupRepository.findMaxSortOrderByUserId(userId).orElse(0);

        List<AppendedGroups.AppendedGroupItem> appendedItems = new ArrayList<>();
        List<List<String>> chunks = partition(delta, groupSize);

        try {
            for (int i = 0; i < chunks.size(); i++) {
                List<String> chunk = chunks.get(i);
                StudyGroup group = new StudyGroup();
                group.setUserId(userId);
                group.setName("第" + (existingAutoCount + i + 1) + "组");
                group.setSource(GroupSource.auto);
                group.setStatus(GroupStatus.not_started);
                group.setSortOrder(maxSortOrder + i + 1);
                group = groupRepository.save(group);

                for (int j = 0; j < chunk.size(); j++) {
                    GroupWord groupWord = new GroupWord();
                    groupWord.setUserId(userId);
                    groupWord.setGroupId(group.getId());
                    groupWord.setWordKey(chunk.get(j));
                    groupWord.setSortOrder(j);
                    groupWordRepository.save(groupWord);
                }

                appendedItems.add(new AppendedGroups.AppendedGroupItem(group.getId(), group.getName(), chunk.size()));
            }
        } catch (DataIntegrityViolationException ex) {
            throw new WordflipException("CONFLICT", "单词已存在于其他分组");
        }

        return new AppendedGroups(appendedItems.size(), appendedItems);
    }

    /** 按 groupSize 切分 delta 列表 */
    static List<List<String>> partition(List<String> words, int groupSize) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < words.size(); i += groupSize) {
            result.add(words.subList(i, Math.min(i + groupSize, words.size())));
        }
        return result;
    }

    private List<StudyGroup> queryGroups(Long userId, GroupSource source, String sort) {
        boolean byName = "name".equalsIgnoreCase(sort);
        if (source == null) {
            return byName
                    ? groupRepository.findByUserIdOrderByNameAsc(userId)
                    : groupRepository.findByUserIdOrderByCreatedAtAsc(userId);
        }
        return byName
                ? groupRepository.findByUserIdAndSourceOrderByNameAsc(userId, source)
                : groupRepository.findByUserIdAndSourceOrderByCreatedAtAsc(userId, source);
    }

    private StudyGroup requireOwnedGroup(Long userId, Long groupId) {
        return groupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "分组不存在"));
    }

    private GroupDetail toGroupDetail(Long userId, StudyGroup group) {
        List<String> wordKeys = groupWordRepository.findByGroupIdOrderBySortOrderAsc(group.getId()).stream()
                .map(GroupWord::getWordKey)
                .toList();
        GroupStats stats = computeStats(userId, wordKeys);
        float progress = computeProgress(userId, wordKeys);
        return GroupDetail.of(group, stats, progress);
    }

    /** 四维统计：无 mastery 记录计 unlearned */
    private GroupStats computeStats(Long userId, List<String> wordKeys) {
        Map<String, WordMastery> masteryByKey = loadMasteryMap(userId, wordKeys);
        int unlearned = 0;
        int fuzzy = 0;
        int unknown = 0;
        for (String key : wordKeys) {
            WordMastery mastery = masteryByKey.get(key);
            if (mastery == null || mastery.getLevel() == MasteryLevel.unlearned) {
                unlearned++;
            } else if (mastery.getLevel() == MasteryLevel.fuzzy) {
                fuzzy++;
            } else {
                unknown++;
            }
        }
        return new GroupStats(unlearned, fuzzy, unknown, wordKeys.size());
    }

    /** progress = count(unlearned ∧ hasQuizHistory) / total（REQ-GROUP-2） */
    private float computeProgress(Long userId, List<String> wordKeys) {
        if (wordKeys.isEmpty()) {
            return 0f;
        }
        Map<String, WordMastery> masteryByKey = loadMasteryMap(userId, wordKeys);
        int numerator = 0;
        for (String key : wordKeys) {
            WordMastery mastery = masteryByKey.get(key);
            if (mastery != null
                    && mastery.getLevel() == MasteryLevel.unlearned
                    && mastery.isHasQuizHistory()) {
                numerator++;
            }
        }
        return (float) numerator / wordKeys.size();
    }

    private Map<String, WordMastery> loadMasteryMap(Long userId, List<String> wordKeys) {
        if (wordKeys.isEmpty()) {
            return Map.of();
        }
        return wordMasteryRepository.findByUserIdAndWordKeyIn(userId, wordKeys).stream()
                .collect(Collectors.toMap(WordMastery::getWordKey, m -> m, (a, b) -> a));
    }

    /** 未入组词 Key 列表（已勾选词书去重 − 已入组） */
    private List<String> listUnassignedWordKeys(Long userId) {
        List<Long> selectedBookIds = userBookSelectionRepository.findBookIdsByUserId(userId);
        if (selectedBookIds.isEmpty()) {
            return List.of();
        }
        Set<String> selected = new HashSet<>(bookWordRepository.findDistinctWordKeysByBookIds(selectedBookIds));
        Set<String> assigned = groupWordRepository.findWordKeysByUserId(userId);
        return selected.stream()
                .filter(key -> !assigned.contains(key))
                .sorted()
                .toList();
    }

    private List<WordSummary> toWordSummaries(Long userId, List<String> wordKeys) {
        Map<String, WordSummary> map = resolveWordSummaries(userId, wordKeys);
        return wordKeys.stream()
                .map(key -> map.getOrDefault(key, new WordSummary(key, key, "", null, null)))
                .toList();
    }

    /** 优先 lexicon，回退 book_words */
    private Map<String, WordSummary> resolveWordSummaries(Long userId, List<String> wordKeys) {
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

    private static boolean matchesQuery(WordSummary summary, String wordKey, String q) {
        if (summary != null) {
            if (summary.en() != null && summary.en().toLowerCase(Locale.ROOT).startsWith(q)) {
                return true;
            }
            if (summary.cn() != null && summary.cn().toLowerCase(Locale.ROOT).startsWith(q)) {
                return true;
            }
        }
        return wordKey.startsWith(q);
    }
}
