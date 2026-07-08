package com.wordflip.service;

import com.wordflip.domain.BookWord;
import com.wordflip.domain.GroupSource;
import com.wordflip.domain.GroupStrategy;
import com.wordflip.domain.StudyGroup;
import com.wordflip.domain.UserSettings;
import com.wordflip.domain.WordFreqRank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GroupService.appendGroupsForNewWords 增量 delta 逻辑单元测试（Q-01 部分）。
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private com.wordflip.repository.UserBookSelectionRepository userBookSelectionRepository;
    @Mock
    private com.wordflip.repository.BookWordRepository bookWordRepository;
    @Mock
    private com.wordflip.repository.GroupWordRepository groupWordRepository;
    @Mock
    private com.wordflip.repository.GroupRepository groupRepository;
    @Mock
    private com.wordflip.repository.UserSettingsRepository userSettingsRepository;
    @Mock
    private BookService bookService;
    @Mock
    private com.wordflip.repository.UserWordLexiconRepository userWordLexiconRepository;
    @Mock
    private com.wordflip.repository.WordMasteryRepository wordMasteryRepository;
    @Mock
    private com.wordflip.repository.WordFreqRankRepository wordFreqRankRepository;

    @InjectMocks
    private GroupService groupService;

    @Test
    void appendGroups_deltaEmpty_returnsEmpty() {
        when(userBookSelectionRepository.findBookIdsByUserIdOrderBySelectedAtAsc(1L)).thenReturn(List.of(1L));
        when(bookWordRepository.findByBookIdInOrderByBookIdAscSortOrderAsc(List.of(1L)))
                .thenReturn(List.of(bookWord(1L, "apple", 0), bookWord(1L, "banana", 1)));
        when(groupWordRepository.findWordKeysByUserId(1L)).thenReturn(Set.of("apple", "banana"));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(defaultSettings()));

        var result = groupService.appendGroupsForNewWords(1L);

        assertThat(result.getCount()).isZero();
        verify(groupRepository, never()).save(any());
    }

    @Test
    void appendGroups_partitionsDeltaByGroupSize() {
        UserSettings settings = defaultSettings();
        settings.setGroupSize(3);

        when(userBookSelectionRepository.findBookIdsByUserIdOrderBySelectedAtAsc(1L)).thenReturn(List.of(1L));
        when(bookWordRepository.findByBookIdInOrderByBookIdAscSortOrderAsc(List.of(1L)))
                .thenReturn(List.of(
                        bookWord(1L, "a", 0),
                        bookWord(1L, "b", 1),
                        bookWord(1L, "c", 2),
                        bookWord(1L, "d", 3),
                        bookWord(1L, "e", 4)
                ));
        when(groupWordRepository.findWordKeysByUserId(1L)).thenReturn(Set.of());
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(groupRepository.findTopByUserIdAndSourceOrderBySortOrderDesc(1L, GroupSource.auto))
                .thenReturn(Optional.empty());
        when(groupRepository.countByUserIdAndSource(1L, GroupSource.auto)).thenReturn(0);
        when(groupRepository.findMaxSortOrderByUserId(1L)).thenReturn(Optional.of(0));
        when(groupRepository.save(any())).thenAnswer(inv -> {
            StudyGroup g = inv.getArgument(0);
            g.setId(100L);
            return g;
        });

        var result = groupService.appendGroupsForNewWords(1L);

        assertThat(result.getCount()).isEqualTo(2);
        assertThat(result.getGroups()).hasSize(2);
        verify(bookService).upsertLexiconForNewWords(eq(1L), eq(List.of("a", "b", "c", "d", "e")), eq(List.of(1L)));
    }

    @Test
    void appendGroups_fillsLastIncompleteAutoGroupBeforeCreatingNew() {
        UserSettings settings = defaultSettings();
        settings.setGroupSize(20);

        StudyGroup lastGroup = new StudyGroup();
        lastGroup.setId(50L);
        lastGroup.setSource(GroupSource.auto);

        when(userBookSelectionRepository.findBookIdsByUserIdOrderBySelectedAtAsc(1L)).thenReturn(List.of(1L));
        when(bookWordRepository.findByBookIdInOrderByBookIdAscSortOrderAsc(List.of(1L)))
                .thenReturn(wordsFromKeys(1L, "w1", "w2", "w3", "w4", "w5", "w6", "w7", "w8", "w9", "w10"));
        when(groupWordRepository.findWordKeysByUserId(1L)).thenReturn(Set.of());
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(groupRepository.findTopByUserIdAndSourceOrderBySortOrderDesc(1L, GroupSource.auto))
                .thenReturn(Optional.of(lastGroup));
        when(groupWordRepository.countByGroupId(50L)).thenReturn(15L);
        when(groupRepository.countByUserIdAndSource(1L, GroupSource.auto)).thenReturn(1);
        when(groupRepository.findMaxSortOrderByUserId(1L)).thenReturn(Optional.of(1));
        when(groupRepository.save(any())).thenAnswer(inv -> {
            StudyGroup g = inv.getArgument(0);
            g.setId(99L);
            return g;
        });

        var result = groupService.appendGroupsForNewWords(1L);

        // 15 + 5 补齐原组，剩余 5 新建 1 组
        assertThat(result.getCount()).isEqualTo(1);
        verify(groupWordRepository, atLeastOnce()).save(any());
        verify(bookService).upsertLexiconForNewWords(
                eq(1L),
                eq(List.of("w1", "w2", "w3", "w4", "w5", "w6", "w7", "w8", "w9", "w10")),
                eq(List.of(1L))
        );
    }

    @Test
    void buildOrderedWordKeys_bookOrder_mergesByBookSelectionOrderAndDedupes() {
        List<Long> bookIds = List.of(2L, 1L);
        when(bookWordRepository.findByBookIdInOrderByBookIdAscSortOrderAsc(bookIds))
                .thenReturn(List.of(
                        bookWord(1L, "shared", 0),
                        bookWord(1L, "only-b1", 1),
                        bookWord(2L, "first-b2", 0),
                        bookWord(2L, "shared", 1)
                ));

        List<String> ordered = groupService.buildOrderedWordKeys(1L, bookIds, GroupStrategy.book_order);

        assertThat(ordered).containsExactly("first-b2", "shared", "only-b1");
    }

    @Test
    void buildOrderedWordKeys_frequency_sortsByGlobalRank() {
        List<Long> bookIds = List.of(1L);
        when(bookWordRepository.findByBookIdInOrderByBookIdAscSortOrderAsc(bookIds))
                .thenReturn(wordsFromKeys(1L, "zebra", "apple", "banana"));
        when(wordFreqRankRepository.findByWordKeyIn(List.of("zebra", "apple", "banana")))
                .thenReturn(List.of(
                        freqRank("apple", 100),
                        freqRank("banana", 200)
                ));

        List<String> ordered = groupService.buildOrderedWordKeys(1L, bookIds, GroupStrategy.frequency);

        assertThat(ordered).containsExactly("apple", "banana", "zebra");
    }

    @Test
    void buildOrderedWordKeys_frequency_unknownWordsFallBackToBookOrderTail() {
        List<Long> bookIds = List.of(1L);
        when(bookWordRepository.findByBookIdInOrderByBookIdAscSortOrderAsc(bookIds))
                .thenReturn(wordsFromKeys(1L, "unknown-a", "apple", "unknown-b"));
        when(wordFreqRankRepository.findByWordKeyIn(List.of("unknown-a", "apple", "unknown-b")))
                .thenReturn(List.of(freqRank("apple", 50)));

        List<String> ordered = groupService.buildOrderedWordKeys(1L, bookIds, GroupStrategy.frequency);

        assertThat(ordered).containsExactly("apple", "unknown-a", "unknown-b");
    }

    @Test
    void buildOrderedWordKeys_random_isStableForSameUserAndBooks() {
        List<Long> bookIds = List.of(1L, 2L);
        List<BookWord> words = wordsFromKeys(1L, "a", "b", "c", "d", "e");
        words.addAll(wordsFromKeys(2L, "f", "g", "h"));
        when(bookWordRepository.findByBookIdInOrderByBookIdAscSortOrderAsc(bookIds)).thenReturn(words);

        List<String> first = groupService.buildOrderedWordKeys(42L, bookIds, GroupStrategy.random);
        List<String> second = groupService.buildOrderedWordKeys(42L, bookIds, GroupStrategy.random);

        assertThat(first).hasSize(8);
        assertThat(first).containsExactlyElementsOf(second);
        assertThat(first).isNotEqualTo(List.of("a", "b", "c", "d", "e", "f", "g", "h"));
    }

    @Test
    void regroupAutoGroups_rebuildsAfterDeletingAutoGroups() {
        UserSettings settings = defaultSettings();
        settings.setGroupSize(2);

        when(userBookSelectionRepository.findBookIdsByUserIdOrderBySelectedAtAsc(1L)).thenReturn(List.of(1L));
        when(groupWordRepository.findWordKeysByUserIdAndGroupSource(1L, GroupSource.custom))
                .thenReturn(Set.of("custom-only"));
        when(bookWordRepository.findByBookIdInOrderByBookIdAscSortOrderAsc(List.of(1L)))
                .thenReturn(wordsFromKeys(1L, "a", "b", "c", "custom-only"));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(groupRepository.findMaxSortOrderByUserId(1L)).thenReturn(Optional.of(5));
        when(groupRepository.save(any())).thenAnswer(inv -> {
            StudyGroup g = inv.getArgument(0);
            g.setId(200L + g.getSortOrder());
            return g;
        });

        var result = groupService.regroupAutoGroups(1L);

        verify(groupRepository).deleteByUserIdAndSource(1L, GroupSource.auto);
        // a,b,c 三个词，custom-only 已在 custom 组跳过 → 2 组
        assertThat(result.getCount()).isEqualTo(2);
        verify(bookService).upsertLexiconForNewWords(eq(1L), eq(List.of("a", "b", "c")), eq(List.of(1L)));
    }

    @Test
    void partition_splitsCorrectly() {
        List<List<String>> chunks = GroupService.partition(List.of("a", "b", "c", "d", "e"), 2);
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).containsExactly("a", "b");
        assertThat(chunks.get(1)).containsExactly("c", "d");
        assertThat(chunks.get(2)).containsExactly("e");
    }

    private static UserSettings defaultSettings() {
        UserSettings settings = new UserSettings();
        settings.setUserId(1L);
        settings.setGroupSize(20);
        settings.setGroupStrategy(GroupStrategy.book_order);
        return settings;
    }

    private static BookWord bookWord(Long bookId, String key, int sortOrder) {
        BookWord word = new BookWord();
        word.setBookId(bookId);
        word.setWordKey(key);
        word.setEn(key);
        word.setCn(key);
        word.setSortOrder(sortOrder);
        return word;
    }

    private static List<BookWord> wordsFromKeys(Long bookId, String... keys) {
        List<BookWord> words = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            words.add(bookWord(bookId, keys[i], i));
        }
        return words;
    }

    private static WordFreqRank freqRank(String key, int rank) {
        WordFreqRank row = new WordFreqRank();
        row.setWordKey(key);
        row.setFreqRank(rank);
        row.setSource("wordfreq");
        return row;
    }
}
