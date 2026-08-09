package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wordflip.domain.GroupStrategy;
import com.wordflip.domain.UserSettings;
import com.wordflip.dto.settings.PreferencesPatchRequest;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.UserSettingsRepository;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设置服务负责保存偏好，并在分组配置实际变化后追加未入组卡片。
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private UserSettingsRepository repository;

    @Mock
    private GroupService groupService;

    @Test
    void savesChangedGroupingBeforeAppendingCurrentPlanCards() {
        UserSettings settings = settings(7L, 19L, 20, GroupStrategy.book_order);
        when(repository.findById(7L)).thenReturn(Optional.of(settings));
        PreferencesPatchRequest request = groupingRequest(30, GroupStrategy.frequency);

        var response = service().patchPreferences(7L, request);

        assertThat(response.getGroupSize()).isEqualTo(30);
        assertThat(response.getGroupStrategy()).isEqualTo(GroupStrategy.frequency);
        InOrder ordered = inOrder(repository, groupService);
        ordered.verify(repository).saveAndFlush(settings);
        ordered.verify(groupService).appendAutoGroups(7L, 19L);
        ordered.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 20, 30, 50})
    void acceptsSupportedGroupSizes(int groupSize) {
        UserSettings settings = settings(7L, 19L, groupSize == 20 ? 30 : 20, GroupStrategy.book_order);
        when(repository.findById(7L)).thenReturn(Optional.of(settings));
        PreferencesPatchRequest request = new PreferencesPatchRequest();
        request.setGroupSize(groupSize);

        var response = service().patchPreferences(7L, request);

        assertThat(response.getGroupSize()).isEqualTo(groupSize);
        verify(repository).saveAndFlush(settings);
        verify(groupService).appendAutoGroups(7L, 19L);
    }

    @Test
    void rejectsUnsupportedGroupSize() {
        UserSettings settings = settings(7L, 19L, 20, GroupStrategy.book_order);
        when(repository.findById(7L)).thenReturn(Optional.of(settings));
        PreferencesPatchRequest request = new PreferencesPatchRequest();
        request.setGroupSize(25);

        assertThatThrownBy(() -> service().patchPreferences(7L, request))
                .isInstanceOfSatisfying(WordflipException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("VALIDATION_ERROR"));

        verify(repository, never()).saveAndFlush(settings);
        verify(groupService, never()).appendAutoGroups(7L, 19L);
    }

    @Test
    void savesUnchangedGroupingWithoutAppendingCards() {
        UserSettings settings = settings(7L, 19L, 20, GroupStrategy.book_order);
        when(repository.findById(7L)).thenReturn(Optional.of(settings));
        PreferencesPatchRequest request = groupingRequest(20, GroupStrategy.book_order);

        service().patchPreferences(7L, request);

        verify(repository).saveAndFlush(settings);
        verifyNoInteractions(groupService);
    }

    @Test
    void savesChangedGroupingWithoutAppendingWhenNoPlanIsActive() {
        UserSettings settings = settings(7L, null, 20, GroupStrategy.book_order);
        when(repository.findById(7L)).thenReturn(Optional.of(settings));
        PreferencesPatchRequest request = groupingRequest(30, GroupStrategy.frequency);

        service().patchPreferences(7L, request);

        verify(repository).saveAndFlush(settings);
        verifyNoInteractions(groupService);
    }

    @Test
    void savesNonGroupingPreferenceWithoutAppendingCards() {
        UserSettings settings = settings(7L, 19L, 20, GroupStrategy.book_order);
        when(repository.findById(7L)).thenReturn(Optional.of(settings));
        PreferencesPatchRequest request = new PreferencesPatchRequest();
        request.setAutoSpeak(false);

        var response = service().patchPreferences(7L, request);

        assertThat(response.isAutoSpeak()).isFalse();
        verify(repository).saveAndFlush(settings);
        verifyNoInteractions(groupService);
    }

    @Test
    void propagatesAppendFailureAfterSavingChangedGrouping() {
        UserSettings settings = settings(7L, 19L, 20, GroupStrategy.book_order);
        when(repository.findById(7L)).thenReturn(Optional.of(settings));
        PreferencesPatchRequest request = groupingRequest(30, GroupStrategy.frequency);
        RuntimeException appendFailure = new RuntimeException("追加失败");
        org.mockito.Mockito.doThrow(appendFailure)
                .when(groupService).appendAutoGroups(7L, 19L);

        assertThatThrownBy(() -> service().patchPreferences(7L, request))
                .isSameAs(appendFailure);

        InOrder ordered = inOrder(repository, groupService);
        ordered.verify(repository).saveAndFlush(settings);
        ordered.verify(groupService).appendAutoGroups(7L, 19L);
    }

    @Test
    void patchPreferencesKeepsSaveAndAppendInOneTransaction() throws NoSuchMethodException {
        Method method = SettingsService.class.getMethod(
                "patchPreferences", Long.class, PreferencesPatchRequest.class);

        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    private SettingsService service() {
        return new SettingsService(repository, groupService);
    }

    private static UserSettings settings(
            Long userId,
            Long activePlanId,
            int groupSize,
            GroupStrategy groupStrategy
    ) {
        UserSettings settings = new UserSettings();
        settings.setUserId(userId);
        settings.setActivePlanId(activePlanId);
        settings.setGroupSize(groupSize);
        settings.setGroupStrategy(groupStrategy);
        return settings;
    }

    private static PreferencesPatchRequest groupingRequest(
            int groupSize,
            GroupStrategy groupStrategy
    ) {
        PreferencesPatchRequest request = new PreferencesPatchRequest();
        request.setGroupSize(groupSize);
        request.setGroupStrategy(groupStrategy);
        return request;
    }
}
