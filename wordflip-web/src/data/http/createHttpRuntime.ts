import type { AxiosInstance } from "axios";
import { AuthSessionManager } from "@/data/http/auth/AuthSessionManager";
import type { AuthResponseDto } from "@/data/http/auth/authDtos";
import { TokenStore } from "@/data/http/auth/TokenStore";
import {
  createAuthenticatedHttpClient,
  createPublicHttpClient
} from "@/data/http/createHttpClient";

export interface HttpRuntime {
  publicClient: AxiosInstance;
  authenticatedClient: AxiosInstance;
  sessions: AuthSessionManager;
}

export interface CreateHttpRuntimeOptions {
  baseURL: string;
  storage: Storage | null;
  now?: () => number;
}

/** 创建共享认证状态的 HTTP 运行时，避免不同业务客户端各自刷新令牌。 */
export function createHttpRuntime({
  baseURL,
  storage,
  now
}: CreateHttpRuntimeOptions): HttpRuntime {
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
  return { publicClient, authenticatedClient, sessions };
}
