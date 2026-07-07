package com.wordflip.service;

import com.wordflip.domain.GroupSource;
import com.wordflip.domain.GroupStatus;
import com.wordflip.domain.GroupWord;
import com.wordflip.domain.StudyGroup;
import com.wordflip.domain.UserSettings;
import com.wordflip.dto.settings.AppendedGroups;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.BookWordRepository;
import com.wordflip.repository.GroupRepository;
import com.wordflip.repository.GroupWordRepository;
import com.wordflip.repository.UserBookSelectionRepository;
import com.wordflip.repository.UserSettingsRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分组增量追加：PUT /settings 后仅 INSERT 新 groups/group_words，不 DELETE 重建。
 */
@Service
public class GroupService {

    private final UserBookSelectionRepository userBookSelectionRepository;
    private final BookWordRepository bookWordRepository;
    private final GroupWordRepository groupWordRepository;
    private final GroupRepository groupRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final BookService bookService;

    public GroupService(
            UserBookSelectionRepository userBookSelectionRepository,
            BookWordRepository bookWordRepository,
            GroupWordRepository groupWordRepository,
            GroupRepository groupRepository,
            UserSettingsRepository userSettingsRepository,
            BookService bookService
    ) {
        this.userBookSelectionRepository = userBookSelectionRepository;
        this.bookWordRepository = bookWordRepository;
        this.groupWordRepository = groupWordRepository;
        this.groupRepository = groupRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.bookService = bookService;
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

        // delta：尚未入组的 wordKey，排序保证确定性
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

        // append 前 upsert lexicon（database-design §6.4）
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
            // UNIQUE(user_id, word_key) 冲突 → 409（P0-B17）
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
}
