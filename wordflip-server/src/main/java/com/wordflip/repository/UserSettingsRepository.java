package com.wordflip.repository;

import com.wordflip.domain.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户设置仓储。
 */
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
}
