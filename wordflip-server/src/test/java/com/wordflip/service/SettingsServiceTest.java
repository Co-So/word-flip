package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wordflip.domain.UserSettings;
import com.wordflip.dto.settings.PreferencesPatchRequest;
import com.wordflip.repository.UserSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 设置服务只处理显示偏好，不再承担选书或分组副作用。
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private UserSettingsRepository repository;

    @Test
    void patchesPreferencesWithoutBookSelectionDependencies() {
        UserSettings settings = new UserSettings();
        settings.setUserId(7L);
        settings.setActivePlanId(19L);
        when(repository.findById(7L)).thenReturn(Optional.of(settings));
        PreferencesPatchRequest request = new PreferencesPatchRequest();
        request.setAutoSpeak(false);

        var response = new SettingsService(repository).patchPreferences(7L, request);

        assertThat(response.getActivePlanId()).isEqualTo(19L);
        assertThat(response.isAutoSpeak()).isFalse();
        verify(repository).save(settings);
    }
}
