package com.wordflip.service;

import com.wordflip.domain.BookWord;
import com.wordflip.domain.GroupSource;
import com.wordflip.domain.GroupStatus;
import com.wordflip.domain.GroupStrategy;
import com.wordflip.domain.GroupWord;
import com.wordflip.domain.HeatDisplayMode;
import com.wordflip.domain.StudyGroup;
import com.wordflip.domain.UserSettings;
import com.wordflip.domain.UserWordLexicon;
import com.wordflip.dto.common.PageMeta;
import com.wordflip.dto.group.CreateCustomGroupRequest;
import com.wordflip.dto.group.GroupDetail;
import com.wordflip.dto.group.GroupListResponse;
import com.wordflip.dto.group.GroupStats;
import com.wordflip.dto.group.GroupWordsResponse;
import com.wordflip.dto.settings.AppendedGroups;
import com.wordflip.dto.word.UnassignedWordsResponse;
import com.wordflip.dto.word.WordProgressSnapshot;
import com.wordflip.dto.word.WordSummary;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.BookWordRepository;
import com.wordflip.repository.GroupRepository;
import com.wordflip.repository.GroupWordRepository;
import com.wordflip.repository.UserBookSelectionRepository;
import com.wordflip.repository.UserSettingsRepository;
import com.wordflip.repository.UserWordLexiconRepository;
import com.wordflip.repository.WordFreqRankRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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
    private final WordFreqRankRepository wordFreqRankRepository;
    private final BookService bookService;
    private final ReviewService reviewService;

    public GroupService(
            UserBookSelectionRepository userBookSelectionRepository,
            BookWordRepository bookWordRepository,
            GroupWordRepository groupWordRepository,
            GroupRepository groupRepository,
            UserSettingsRepository userSettingsRepository,
            UserWordLexiconRepository userWordLexiconRepository,
            WordFreqRankRepository wordFreqRankRepository,
            BookService bookService,
            ReviewService reviewService
    ) {
        this.userBookSelectionRepository = userBookSelectionRepository;
        this.bookWordRepository = bookWordRepository;
        this.groupWordRepository = groupWordRepository;
        this.groupRepository = groupRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.userWordLexiconRepository = userWordLexiconRepository;
        this.wordFreqRankRepository = wordFreqRankRepository;
        this.bookService = bookService;
        this.reviewService = reviewService;
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

    /** GET /groups/{groupId}/words：分页 + 双 skill 进度（按 heatDisplayMode） */
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
        HeatDisplayMode heatMode = resolveHeatDisplayMode(userId);
        Map<String, WordProgressSnapshot> progressByKey =
                reviewService.buildWordProgressMap(userId, wordKeys, heatMode);

        List<GroupWordsResponse.GroupWordItem> items = wordKeys.stream()
                .map(key -> {
                    WordSummary summary = summaries.get(key);
                    if (summary == null) {
                        summary = new WordSummary(key, key, "", null, null);
                    }
                    WordProgressSnapshot progress = progressByKey.getOrDefault(
                            key,
                            WordProgressSnapshot.empty(heatMode)
                    );
                    return GroupWordsResponse.GroupWordItem.from(summary, progress);
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
     * 增量追加分组（api-modules §2.1、REQ-BOOK-22~25）：
     * 按 groupStrategy 合并去重 → delta → 先补齐未满 auto 组 → 切分新组。
     */
    @Transactional
    public AppendedGroups appendGroupsForNewWords(Long userId) {
        List<Long> selectedBookIds = userBookSelectionRepository.findBookIdsByUserIdOrderBySelectedAtAsc(userId);
        if (selectedBookIds.isEmpty()) {
            return AppendedGroups.empty();
        }

        UserSettings settings = userSettingsRepository.findById(userId).orElseGet(UserSettings::new);
        int groupSize = settings.getGroupSize() > 0 ? settings.getGroupSize() : 20;
        GroupStrategy strategy = settings.getGroupStrategy() != null
                ? settings.getGroupStrategy()
                : GroupStrategy.book_order;

        List<String> orderedKeys = buildOrderedWordKeys(userId, selectedBookIds, strategy);
        Set<String> assignedWordKeys = groupWordRepository.findWordKeysByUserId(userId);

        // delta 保持策略顺序，不再字母排序
        List<String> delta = orderedKeys.stream()
                .filter(key -> !assignedWordKeys.contains(key))
                .collect(Collectors.toList());

        if (delta.isEmpty()) {
            return AppendedGroups.empty();
        }

        bookService.upsertLexiconForNewWords(userId, delta, selectedBookIds);

        List<AppendedGroups.AppendedGroupItem> appendedItems = new ArrayList<>();
        List<String> remainingDelta = new ArrayList<>(delta);

        try {
            // REQ-BOOK-25：最后一个 auto 组未满时先补齐
            Optional<StudyGroup> lastAutoGroup = groupRepository.findTopByUserIdAndSourceOrderBySortOrderDesc(
                    userId,
                    GroupSource.auto
            );
            if (lastAutoGroup.isPresent()) {
                StudyGroup lastGroup = lastAutoGroup.get();
                int currentCount = (int) groupWordRepository.countByGroupId(lastGroup.getId());
                int slots = groupSize - currentCount;
                if (slots > 0 && !remainingDelta.isEmpty()) {
                    int fillCount = Math.min(slots, remainingDelta.size());
                    List<String> toFill = new ArrayList<>(remainingDelta.subList(0, fillCount));
                    appendWordsToGroup(userId, lastGroup.getId(), toFill, currentCount);
                    remainingDelta = new ArrayList<>(remainingDelta.subList(fillCount, remainingDelta.size()));
                }
            }

            if (remainingDelta.isEmpty()) {
                return new AppendedGroups(0, appendedItems);
            }

            int existingAutoCount = groupRepository.countByUserIdAndSource(userId, GroupSource.auto);
            int maxSortOrder = groupRepository.findMaxSortOrderByUserId(userId).orElse(0);
            List<List<String>> chunks = partition(remainingDelta, groupSize);

            for (int i = 0; i < chunks.size(); i++) {
                List<String> chunk = chunks.get(i);
                StudyGroup group = new StudyGroup();
                group.setUserId(userId);
                group.setName("第" + (existingAutoCount + i + 1) + "组");
                group.setSource(GroupSource.auto);
                group.setStatus(GroupStatus.not_started);
                group.setSortOrder(maxSortOrder + i + 1);
                group = groupRepository.save(group);

                appendWordsToGroup(userId, group.getId(), chunk, 0);

                appendedItems.add(new AppendedGroups.AppendedGroupItem(group.getId(), group.getName(), chunk.size()));
            }
        } catch (DataIntegrityViolationException ex) {
            throw new WordflipException("CONFLICT", "单词已存在于其他分组");
        }

        return new AppendedGroups(appendedItems.size(), appendedItems);
    }

    /**
     * 重新分组（REQ-BOOK-26）：删除全部 auto 组，按当前勾选词书 + 策略重建；
     * custom 组及其单词保留；掌握度/图片/污渍不删。
     */
    @Transactional
    public AppendedGroups regroupAutoGroups(Long userId) {
        List<Long> selectedBookIds = userBookSelectionRepository.findBookIdsByUserIdOrderBySelectedAtAsc(userId);
        if (selectedBookIds.isEmpty()) {
            throw new WordflipException("VALIDATION_ERROR", "请至少勾选一本词书后再重新分组");
        }

        UserSettings settings = userSettingsRepository.findById(userId).orElseGet(UserSettings::new);
        int groupSize = settings.getGroupSize() > 0 ? settings.getGroupSize() : 20;
        GroupStrategy strategy = settings.getGroupStrategy() != null
                ? settings.getGroupStrategy()
                : GroupStrategy.book_order;

        // 保留 custom 组内词，避免违反一词一组
        Set<String> customWordKeys = groupWordRepository.findWordKeysByUserIdAndGroupSource(
                userId,
                GroupSource.custom
        );

        groupRepository.deleteByUserIdAndSource(userId, GroupSource.auto);

        List<String> orderedKeys = buildOrderedWordKeys(userId, selectedBookIds, strategy);
        List<String> toAssign = orderedKeys.stream()
                .filter(key -> !customWordKeys.contains(key))
                .collect(Collectors.toList());

        if (toAssign.isEmpty()) {
            return AppendedGroups.empty();
        }

        bookService.upsertLexiconForNewWords(userId, toAssign, selectedBookIds);

        int maxSortOrder = groupRepository.findMaxSortOrderByUserId(userId).orElse(0);
        List<AppendedGroups.AppendedGroupItem> items = new ArrayList<>();
        List<List<String>> chunks = partition(toAssign, groupSize);

        try {
            for (int i = 0; i < chunks.size(); i++) {
                List<String> chunk = chunks.get(i);
                StudyGroup group = new StudyGroup();
                group.setUserId(userId);
                group.setName("第" + (i + 1) + "组");
                group.setSource(GroupSource.auto);
                group.setStatus(GroupStatus.not_started);
                group.setSortOrder(maxSortOrder + i + 1);
                group = groupRepository.save(group);
                appendWordsToGroup(userId, group.getId(), chunk, 0);
                items.add(new AppendedGroups.AppendedGroupItem(group.getId(), group.getName(), chunk.size()));
            }
        } catch (DataIntegrityViolationException ex) {
            throw new WordflipException("CONFLICT", "单词已存在于其他分组");
        }

        return new AppendedGroups(items.size(), items);
    }

    /**
     * 按 groupStrategy 合并已勾选词书词条（REQ-BOOK-22~24）。
     * book_order：词书勾选顺序 + 书内 sort_order，去重保序；
     * frequency：按 word_freq_ranks 全局 rank 升序；无 rank 词排末尾并保持 book_order 相对序；
     * random：稳定随机（seed = userId + bookIds）。
     */
    List<String> buildOrderedWordKeys(Long userId, List<Long> bookIdsOrdered, GroupStrategy strategy) {
        if (bookIdsOrdered.isEmpty()) {
            return List.of();
        }

        List<BookWord> words = new ArrayList<>(
                bookWordRepository.findByBookIdInOrderByBookIdAscSortOrderAsc(bookIdsOrdered)
        );
        Map<Long, Integer> bookRank = new HashMap<>();
        for (int i = 0; i < bookIdsOrdered.size(); i++) {
            bookRank.put(bookIdsOrdered.get(i), i);
        }
        words.sort(Comparator
                .comparingInt((BookWord w) -> bookRank.getOrDefault(w.getBookId(), Integer.MAX_VALUE))
                .thenComparingInt(BookWord::getSortOrder));

        List<String> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (BookWord word : words) {
            if (seen.add(word.getWordKey())) {
                ordered.add(word.getWordKey());
            }
        }

        if (strategy == GroupStrategy.random) {
            Collections.shuffle(ordered, new Random(stableRandomSeed(userId, bookIdsOrdered)));
        } else if (strategy == GroupStrategy.frequency) {
            ordered = sortByFrequencyRank(ordered);
        }

        return ordered;
    }

    /** 有 rank 的按 freq_rank ASC；无 rank 词保持 book_order 相对顺序排在末尾 */
    private List<String> sortByFrequencyRank(List<String> bookOrderKeys) {
        if (bookOrderKeys.isEmpty()) {
            return bookOrderKeys;
        }
        Map<String, Integer> rankByKey = wordFreqRankRepository.findByWordKeyIn(bookOrderKeys).stream()
                .collect(Collectors.toMap(
                        w -> w.getWordKey(),
                        w -> w.getFreqRank(),
                        (a, b) -> Math.min(a, b)
                ));
        Map<String, Integer> bookOrderIndex = new HashMap<>();
        for (int i = 0; i < bookOrderKeys.size(); i++) {
            bookOrderIndex.put(bookOrderKeys.get(i), i);
        }
        List<String> sorted = new ArrayList<>(bookOrderKeys);
        sorted.sort((a, b) -> {
            Integer rankA = rankByKey.get(a);
            Integer rankB = rankByKey.get(b);
            if (rankA != null && rankB != null) {
                return Integer.compare(rankA, rankB);
            }
            if (rankA != null) {
                return -1;
            }
            if (rankB != null) {
                return 1;
            }
            return Integer.compare(bookOrderIndex.get(a), bookOrderIndex.get(b));
        });
        return sorted;
    }

    /** 稳定随机种子：相同 userId + bookIds 列表产生相同打乱顺序 */
    static long stableRandomSeed(Long userId, List<Long> bookIds) {
        long seed = userId != null ? userId : 0L;
        for (Long bookId : bookIds) {
            seed = 31 * seed + (bookId != null ? bookId : 0L);
        }
        return seed;
    }

    private void appendWordsToGroup(Long userId, Long groupId, List<String> wordKeys, int startSortOrder) {
        for (int i = 0; i < wordKeys.size(); i++) {
            GroupWord groupWord = new GroupWord();
            groupWord.setUserId(userId);
            groupWord.setGroupId(groupId);
            groupWord.setWordKey(wordKeys.get(i));
            groupWord.setSortOrder(startSortOrder + i);
            groupWordRepository.save(groupWord);
        }
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
        HeatDisplayMode heatMode = resolveHeatDisplayMode(userId);
        Map<String, WordProgressSnapshot> progressByKey =
                reviewService.buildWordProgressMap(userId, wordKeys, heatMode);
        GroupStats stats = computeStats(wordKeys, progressByKey, heatMode);
        float progress = computeProgress(wordKeys, progressByKey, heatMode);
        return GroupDetail.of(group, stats, progress);
    }

    /** 热力分档统计：按用户 heatDisplayMode 的 displayStability（REQ-GROUP-2） */
    private GroupStats computeStats(
            List<String> wordKeys,
            Map<String, WordProgressSnapshot> progressByKey,
            HeatDisplayMode heatMode
    ) {
        int heat0 = 0;
        int heat1 = 0;
        int heat2 = 0;
        int heat3 = 0;
        int heat4 = 0;
        for (String key : wordKeys) {
            WordProgressSnapshot progress = progressByKey.getOrDefault(
                    key,
                    WordProgressSnapshot.empty(heatMode)
            );
            switch (progress.heatBucket()) {
                case 1 -> heat1++;
                case 2 -> heat2++;
                case 3 -> heat3++;
                case 4 -> heat4++;
                default -> heat0++;
            }
        }
        return new GroupStats(heat0, heat1, heat2, heat3, heat4, wordKeys.size());
    }

    /** progress = count(displayStability >= 80) / total（REQ-GROUP-2） */
    private float computeProgress(
            List<String> wordKeys,
            Map<String, WordProgressSnapshot> progressByKey,
            HeatDisplayMode heatMode
    ) {
        if (wordKeys.isEmpty()) {
            return 0f;
        }
        int numerator = 0;
        for (String key : wordKeys) {
            WordProgressSnapshot progress = progressByKey.getOrDefault(
                    key,
                    WordProgressSnapshot.empty(heatMode)
            );
            if (progress.displayStability() >= 80.0) {
                numerator++;
            }
        }
        return (float) numerator / wordKeys.size();
    }

    private HeatDisplayMode resolveHeatDisplayMode(Long userId) {
        return userSettingsRepository.findById(userId)
                .map(UserSettings::getHeatDisplayMode)
                .orElse(HeatDisplayMode.combined);
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
