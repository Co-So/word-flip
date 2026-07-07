package com.wordflip.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private GroupService groupService;

    @Test
    void appendGroups_deltaEmpty_returnsEmpty() {
        when(userBookSelectionRepository.findBookIdsByUserId(1L)).thenReturn(List.of(1L));
        when(bookWordRepository.findDistinctWordKeysByBookIds(List.of(1L)))
                .thenReturn(List.of("apple", "banana"));
        when(groupWordRepository.findWordKeysByUserId(1L)).thenReturn(Set.of("apple", "banana"));

        var result = groupService.appendGroupsForNewWords(1L);

        assertThat(result.getCount()).isZero();
        verify(groupRepository, never()).save(any());
        verify(userSettingsRepository, never()).findById(1L);
    }

    @Test
    void appendGroups_partitionsDeltaByGroupSize() {
        com.wordflip.domain.UserSettings settings = new com.wordflip.domain.UserSettings();
        settings.setUserId(1L);
        settings.setGroupSize(3);

        when(userBookSelectionRepository.findBookIdsByUserId(1L)).thenReturn(List.of(1L));
        when(bookWordRepository.findDistinctWordKeysByBookIds(List.of(1L)))
                .thenReturn(List.of("a", "b", "c", "d", "e"));
        when(groupWordRepository.findWordKeysByUserId(1L)).thenReturn(Set.of());
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(groupRepository.countByUserIdAndSource(1L, com.wordflip.domain.GroupSource.auto)).thenReturn(0);
        when(groupRepository.findMaxSortOrderByUserId(1L)).thenReturn(Optional.of(0));
        when(groupRepository.save(any())).thenAnswer(inv -> {
            com.wordflip.domain.StudyGroup g = inv.getArgument(0);
            g.setId(100L);
            return g;
        });

        var result = groupService.appendGroupsForNewWords(1L);

        assertThat(result.getCount()).isEqualTo(2);
        assertThat(result.getGroups()).hasSize(2);
        verify(bookService).upsertLexiconForNewWords(eq(1L), eq(List.of("a", "b", "c", "d", "e")), eq(List.of(1L)));
    }

    @Test
    void partition_splitsCorrectly() {
        List<List<String>> chunks = GroupService.partition(List.of("a", "b", "c", "d", "e"), 2);
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).containsExactly("a", "b");
        assertThat(chunks.get(1)).containsExactly("c", "d");
        assertThat(chunks.get(2)).containsExactly("e");
    }
}
