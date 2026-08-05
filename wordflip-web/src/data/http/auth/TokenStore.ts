import type { AuthResponseDto } from "@/data/http/auth/authDtos";

export const AUTH_STORAGE_KEY = "wordflip:web:auth:v1";

type AuthStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export interface StoredAuthRecord {
  schemaVersion: 1;
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: number;
  user: AuthResponseDto["user"];
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === "string";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isStoredAuthRecord(value: unknown): value is StoredAuthRecord {
  if (!isRecord(value) || !isRecord(value.user)) return false;
  return value.schemaVersion === 1
    && typeof value.accessToken === "string"
    && value.accessToken.length > 0
    && typeof value.refreshToken === "string"
    && value.refreshToken.length > 0
    && typeof value.accessExpiresAt === "number"
    && Number.isFinite(value.accessExpiresAt)
    && value.accessExpiresAt > 0
    && typeof value.user.id === "number"
    && Number.isFinite(value.user.id)
    && value.user.id > 0
    && isNullableString(value.user.email)
    && isNullableString(value.user.phone);
}

/** 版本化保存认证令牌；损坏数据必须立即失效，不能恢复为半登录状态。 */
export class TokenStore {
  constructor(private readonly storage: AuthStorage | null) {}

  read(): StoredAuthRecord | null {
    if (!this.storage) return null;
    const persisted = this.storage.getItem(AUTH_STORAGE_KEY);
    if (!persisted) return null;

    try {
      const parsed: unknown = JSON.parse(persisted);
      if (isStoredAuthRecord(parsed)) return parsed;
    } catch {
      // 非法 JSON 与旧版本记录统一按未登录处理。
    }

    this.storage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }

  write(record: StoredAuthRecord): void {
    this.storage?.setItem(AUTH_STORAGE_KEY, JSON.stringify(record));
  }

  clear(): void {
    this.storage?.removeItem(AUTH_STORAGE_KEY);
  }
}
