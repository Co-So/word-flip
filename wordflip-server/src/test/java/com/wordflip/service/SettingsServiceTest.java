package com.wordflip.service;

import com.wordflip.domain.GroupStrategy;
import com.wordflip.domain.UserSettings;
import com.wordflip.dto.settings.AppendedGroups;
import com.wordflip.dto.settings.BooksSummary;
import com.wordflip.dto.settings.SaveBooksSettingsRequest;
import com.wordflip.repository.UserBookSelectionRepository;
import com.wordflip.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Settings 保存后 Today 缓存失效（regroup 后 recommendedStudy.groupId 须重建）。
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private UserBookSelectionRepository userBookSelectionRepository;
    @Mock
    private BookService bookService;
    @Mock
    private GroupService groupService;
    @Mock
    private TodayCacheService todayCacheService;

    @InjectMocks
    private SettingsService settingsService;

    @Test
    void saveBooksSettings_regroup_invalidatesTodayCacheForUser() {
        SaveBooksSettingsRequest request = new SaveBooksSettingsRequest();
        request.setBookIds(List.of(1L));
        request.setGroupSize(20);
        request.setRegroup(true);

        UserSettings settings = new UserSettings();
        settings.setUserId(7L);
        settings.setGroupSize(20);
        settings.setGroupStrategy(GroupStrategy.book_order);

        when(userSettingsRepository.findById(7L)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userBookSelectionRepository.findBookIdsByUserId(7L)).thenReturn(List.of(1L));
        when(groupService.regroupAutoGroups(7L)).thenReturn(new AppendedGroups(1, List.of()));
        when(bookService.buildSummary(7L, 20)).thenReturn(new BooksSummary(100, 20, 5));

        settingsService.saveBooksSettings(7L, request);

        verify(groupService).regroupAutoGroups(7L);
        verify(groupService, never()).appendGroupsForNewWords(any());
        verify(todayCacheService).invalidateAllForUser(7L);
    }

    @Test
    void saveBooksSettings_appendAlsoInvalidatesTodayCacheForUser() {
        SaveBooksSettingsRequest request = new SaveBooksSettingsRequest();
        request.setBookIds(List.of(1L, 2L));
        request.setGroupSize(20);
        request.setRegroup(false);

        UserSettings settings = new UserSettings();
        settings.setUserId(7L);
        settings.setGroupSize(20);

        when(userSettingsRepository.findById(7L)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userBookSelectionRepository.findBookIdsByUserId(7L)).thenReturn(List.of(1L, 2L));
        when(groupService.appendGroupsForNewWords(7L)).thenReturn(new AppendedGroups(0, List.of()));
        when(bookService.buildSummary(7L, 20)).thenReturn(new BooksSummary(200, 20, 10));

        settingsService.saveBooksSettings(7L, request);

        verify(groupService).appendGroupsForNewWords(7L);
        verify(todayCacheService).invalidateAllForUser(eq(7L));
    }
}
