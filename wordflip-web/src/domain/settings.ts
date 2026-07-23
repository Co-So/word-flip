export interface AppSettings {
  soundEnabled: boolean;
  reducedMotion: boolean;
}

export interface SettingsRepository {
  getSettings(): Promise<AppSettings>;
  updateSettings(settings: AppSettings): Promise<AppSettings>;
}
