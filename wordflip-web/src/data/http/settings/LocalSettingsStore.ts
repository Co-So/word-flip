const STORAGE_KEY = "wordflip.web.settings.v1";

interface StoredSettings {
  version: 1;
  reducedMotion: boolean;
}

function isStoredSettings(value: unknown): value is StoredSettings {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return Object.keys(candidate).length === 2
    && candidate.version === 1
    && typeof candidate.reducedMotion === "boolean";
}

/** 只保存当前设备的动效偏好，不混入账号或服务端设置。 */
export class LocalSettingsStore {
  constructor(private readonly storage: Storage | null) {}

  read(): boolean {
    if (!this.storage) return false;
    try {
      const persisted = this.storage.getItem(STORAGE_KEY);
      if (!persisted) return false;
      const parsed: unknown = JSON.parse(persisted);
      return isStoredSettings(parsed) ? parsed.reducedMotion : false;
    } catch {
      return false;
    }
  }

  write(reducedMotion: boolean): void {
    if (!this.storage) return;
    const record: StoredSettings = { version: 1, reducedMotion };
    try {
      this.storage.setItem(STORAGE_KEY, JSON.stringify(record));
    } catch {
      // 本地动效偏好是 best-effort，配额或安全策略不得覆盖已成功的服务端保存。
    }
  }
}
