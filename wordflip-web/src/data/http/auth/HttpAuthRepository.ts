import type { AxiosInstance } from "axios";
import { AuthSessionManager } from "@/data/http/auth/AuthSessionManager";
import type { AuthResponseDto } from "@/data/http/auth/authDtos";
import { TokenStore } from "@/data/http/auth/TokenStore";
import {
  createAuthenticatedHttpClient,
  createPublicHttpClient
} from "@/data/http/createHttpClient";
import type {
  AuthRepository,
  AuthSession,
  RegisterInput,
  SignInInput
} from "@/domain/auth";

const phonePattern = /^\+[1-9]\d{1,14}$/;

function normalizeAccount(account: string): string {
  const normalized = account.trim();
  return phonePattern.test(normalized) ? normalized : normalized.toLowerCase();
}

/** 严格按 OpenAPI Auth 契约发送请求，并把 HTTP DTO 转换为页面会话。 */
export class HttpAuthRepository implements AuthRepository {
  constructor(
    private readonly publicClient: AxiosInstance,
    private readonly authenticatedClient: AxiosInstance,
    private readonly sessions: AuthSessionManager
  ) {}

  getSession(): Promise<AuthSession | null> {
    return this.sessions.getSession();
  }

  async signIn(input: SignInInput): Promise<AuthSession> {
    const response = await this.publicClient.post<AuthResponseDto>("/auth/login", {
      account: normalizeAccount(input.account),
      password: input.password
    });
    return this.sessions.startSession(response.data);
  }

  async register(input: RegisterInput): Promise<AuthSession> {
    const account = normalizeAccount(input.account);
    const body = phonePattern.test(account)
      ? { phone: account, password: input.password }
      : { email: account, password: input.password };
    const response = await this.publicClient.post<AuthResponseDto>("/auth/register", body);
    return this.sessions.startSession(response.data);
  }

  async signOut(): Promise<{ signedOut: true }> {
    const refreshToken = this.sessions.getRefreshToken();
    try {
      if (refreshToken) {
        await this.authenticatedClient.post("/auth/logout", { refreshToken });
      }
    } catch {
      // 远端不可用不能阻止当前浏览器退出；服务端令牌仍会按有效期失效。
    } finally {
      this.sessions.clearSession();
    }
    return { signedOut: true };
  }
}

export function createHttpAuthRepository({
  baseURL,
  storage,
  now
}: {
  baseURL: string;
  storage: Storage | null;
  now?: () => number;
}): HttpAuthRepository {
  const publicClient = createPublicHttpClient(baseURL);
  const tokens = new TokenStore(storage);
  const sessions = new AuthSessionManager(
    tokens,
    async (refreshToken) => {
      const response = await publicClient.post<AuthResponseDto>("/auth/refresh", { refreshToken });
      return response.data;
    },
    now
  );
  const authenticatedClient = createAuthenticatedHttpClient({ baseURL, sessions });
  return new HttpAuthRepository(publicClient, authenticatedClient, sessions);
}
