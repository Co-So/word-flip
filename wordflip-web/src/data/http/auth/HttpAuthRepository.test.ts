import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from "axios";
import { AuthSessionManager } from "@/data/http/auth/AuthSessionManager";
import type { AuthResponseDto } from "@/data/http/auth/authDtos";
import { HttpAuthRepository } from "@/data/http/auth/HttpAuthRepository";
import { TokenStore } from "@/data/http/auth/TokenStore";
import {
  createAuthenticatedHttpClient,
  createPublicHttpClient
} from "@/data/http/createHttpClient";

const authResponse: AuthResponseDto = {
  accessToken: "access-token",
  refreshToken: "refresh-token",
  expiresIn: 60,
  user: { id: 42, email: "linmo@example.test", phone: null }
};

function okResponse<T>(config: InternalAxiosRequestConfig, data: T, status = 200): AxiosResponse<T> {
  return {
    data,
    status,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config
  };
}

function networkError(config: InternalAxiosRequestConfig): AxiosError {
  return new AxiosError("Network Error", AxiosError.ERR_NETWORK, config);
}

function createHarness({
  publicAdapter,
  authenticatedAdapter,
  now = () => 1_000
}: {
  publicAdapter: AxiosAdapter;
  authenticatedAdapter?: AxiosAdapter;
  now?: () => number;
}) {
  const publicClient = createPublicHttpClient("/api/v1");
  publicClient.defaults.adapter = publicAdapter;
  const tokens = new TokenStore(window.localStorage);
  const sessions = new AuthSessionManager(
    tokens,
    async (refreshToken) => {
      const response = await publicClient.post<AuthResponseDto>("/auth/refresh", { refreshToken });
      return response.data;
    },
    now
  );
  const authenticatedClient = createAuthenticatedHttpClient({ baseURL: "/api/v1", sessions });
  if (authenticatedAdapter) authenticatedClient.defaults.adapter = authenticatedAdapter;
  return {
    repository: new HttpAuthRepository(publicClient, authenticatedClient, sessions),
    sessions,
    tokens
  };
}

beforeEach(() => {
  window.localStorage.clear();
});

test.each([
  ["linmo@example.test", { email: "linmo@example.test", password: "wordflip-demo" }],
  ["+8613800138000", { phone: "+8613800138000", password: "wordflip-demo" }]
])("注册账号 %s 按 OpenAPI 发送正确字段", async (account, expectedBody) => {
  const requests: InternalAxiosRequestConfig[] = [];
  const { repository } = createHarness({
    publicAdapter: async (config) => {
      requests.push(config);
      return okResponse(config, authResponse, 201);
    }
  });

  await expect(repository.register({ account, password: "wordflip-demo" }))
    .resolves.toMatchObject({ userId: "42", authenticated: true });
  expect(requests[0].url).toBe("/auth/register");
  expect(JSON.parse(String(requests[0].data))).toEqual(expectedBody);
});

test("登录发送 account 并把认证响应保存为本地会话", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const { repository, tokens } = createHarness({
    publicAdapter: async (config) => {
      requests.push(config);
      return okResponse(config, authResponse);
    }
  });

  await expect(repository.signIn({
    account: "linmo@example.test",
    password: "wordflip-demo"
  })).resolves.toEqual({
    userId: "42",
    displayName: "linmo@example.test",
    authenticated: true
  });
  expect(requests[0].url).toBe("/auth/login");
  expect(JSON.parse(String(requests[0].data))).toEqual({
    account: "linmo@example.test",
    password: "wordflip-demo"
  });
  expect(tokens.read()).toMatchObject({
    accessToken: "access-token",
    refreshToken: "refresh-token"
  });
});

test("恢复过期会话时调用 refresh 端点并轮换令牌", async () => {
  let refreshBody: unknown;
  const refreshedResponse = {
    ...authResponse,
    accessToken: "access-new",
    refreshToken: "refresh-new"
  };
  const { repository, sessions, tokens } = createHarness({
    publicAdapter: async (config) => {
      refreshBody = JSON.parse(String(config.data));
      return okResponse(config, refreshedResponse);
    },
    now: () => 10_000
  });
  sessions.startSession({ ...authResponse, expiresIn: 0 });

  await expect(repository.getSession()).resolves.toMatchObject({ userId: "42" });
  expect(refreshBody).toEqual({ refreshToken: "refresh-token" });
  expect(tokens.read()).toMatchObject({
    accessToken: "access-new",
    refreshToken: "refresh-new"
  });
});

test("登出携带当前 Refresh Token 和 Access Token", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const { repository, sessions, tokens } = createHarness({
    publicAdapter: async (config) => okResponse(config, authResponse),
    authenticatedAdapter: async (config) => {
      requests.push(config);
      return okResponse(config, undefined, 204);
    }
  });
  sessions.startSession(authResponse);

  await expect(repository.signOut()).resolves.toEqual({ signedOut: true });
  expect(requests[0].url).toBe("/auth/logout");
  expect(requests[0].headers.get("Authorization")?.toString()).toBe("Bearer access-token");
  expect(JSON.parse(String(requests[0].data))).toEqual({ refreshToken: "refresh-token" });
  expect(tokens.read()).toBeNull();
});

test("远端登出网络失败仍完成当前浏览器本地退出", async () => {
  const { repository, sessions, tokens } = createHarness({
    publicAdapter: async (config) => okResponse(config, authResponse),
    authenticatedAdapter: async (config) => {
      throw networkError(config);
    }
  });
  sessions.startSession(authResponse);

  await expect(repository.signOut()).resolves.toEqual({ signedOut: true });
  expect(tokens.read()).toBeNull();
});
