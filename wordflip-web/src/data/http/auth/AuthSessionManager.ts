import type { AuthResponseDto } from "@/data/http/auth/authDtos";
import type { TokenStore, StoredAuthRecord } from "@/data/http/auth/TokenStore";
import type { AppError } from "@/data/contracts/AppError";
import type { AuthSession } from "@/domain/auth";

type RefreshTokens = (refreshToken: string) => Promise<AuthResponseDto>;

function toSession(user: StoredAuthRecord["user"]): AuthSession {
  return {
    userId: String(user.id),
    displayName: user.email ?? user.phone ?? `用户 ${user.id}`,
    authenticated: true
  };
}

function missingSessionError(): AppError {
  return { kind: "unauthorized", message: "登录状态已失效，请重新登录" };
}

/** 管理认证响应到本地会话的映射，并保证 Refresh Token 只轮换一次。 */
export class AuthSessionManager {
  private refreshPromise: Promise<string> | null = null;

  constructor(
    private readonly tokens: TokenStore,
    private readonly refresh: RefreshTokens,
    private readonly now: () => number = Date.now
  ) {}

  startSession(response: AuthResponseDto): AuthSession {
    const record: StoredAuthRecord = {
      schemaVersion: 1,
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      accessExpiresAt: this.now() + response.expiresIn * 1_000,
      user: response.user
    };
    this.tokens.write(record);
    return toSession(record.user);
  }

  async getSession(): Promise<AuthSession | null> {
    const record = this.tokens.read();
    if (!record) return null;
    if (record.accessExpiresAt > this.now()) return toSession(record.user);

    try {
      await this.refreshAccessToken();
      const refreshed = this.tokens.read();
      return refreshed ? toSession(refreshed.user) : null;
    } catch {
      return null;
    }
  }

  getAccessToken(): string | null {
    return this.tokens.read()?.accessToken ?? null;
  }

  getRefreshToken(): string | null {
    return this.tokens.read()?.refreshToken ?? null;
  }

  refreshAccessToken(): Promise<string> {
    if (this.refreshPromise) return this.refreshPromise;

    const record = this.tokens.read();
    if (!record) {
      this.clearSession();
      return Promise.reject(missingSessionError());
    }

    const pending = this.refresh(record.refreshToken)
      .then((response) => {
        this.startSession(response);
        return response.accessToken;
      })
      .catch((error: unknown) => {
        this.clearSession();
        throw error;
      })
      .finally(() => {
        if (this.refreshPromise === pending) this.refreshPromise = null;
      });
    this.refreshPromise = pending;
    return pending;
  }

  clearSession(): void {
    this.tokens.clear();
  }
}
