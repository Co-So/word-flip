import {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from "axios";
import type { AuthResponseDto } from "@/data/http/auth/authDtos";
import { createHttpRuntime } from "@/data/http/createHttpRuntime";

const initialResponse: AuthResponseDto = {
  accessToken: "access-old",
  refreshToken: "refresh-old",
  expiresIn: 60,
  user: { id: 42, email: "linmo@example.test", phone: null }
};

const refreshedResponse: AuthResponseDto = {
  ...initialResponse,
  accessToken: "access-new",
  refreshToken: "refresh-new"
};

function okResponse<T>(config: InternalAxiosRequestConfig, data: T): AxiosResponse<T> {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config
  };
}

function unauthorized(config: InternalAxiosRequestConfig): AxiosError {
  const response: AxiosResponse = {
    data: { code: "UNAUTHORIZED", message: "Authentication required" },
    status: 401,
    statusText: "Unauthorized",
    headers: new AxiosHeaders(),
    config
  };
  return new AxiosError("unauthorized", AxiosError.ERR_BAD_REQUEST, config, undefined, response);
}

beforeEach(() => {
  window.localStorage.clear();
});

test("创建可共享的公开客户端、认证客户端和会话管理器", () => {
  const runtime = createHttpRuntime({
    baseURL: "/api/v1",
    storage: window.localStorage,
    now: () => 1_000
  });

  expect(runtime.publicClient.defaults.baseURL).toBe("/api/v1");
  expect(runtime.authenticatedClient.defaults.baseURL).toBe("/api/v1");
  expect(runtime.publicClient).not.toBe(runtime.authenticatedClient);
  expect(runtime.sessions.getAccessToken()).toBeNull();
});

test("并发 401 请求只刷新一次且都以新令牌重放", async () => {
  const runtime = createHttpRuntime({
    baseURL: "/api/v1",
    storage: window.localStorage,
    now: () => 1_000
  });
  runtime.sessions.startSession(initialResponse);

  let oldTokenRequests = 0;
  let releaseOldTokenFailures: () => void;
  const bothOldTokenRequests = new Promise<void>((resolve) => {
    releaseOldTokenFailures = resolve;
  });
  let refreshCalls = 0;
  let resolveRefresh: (response: AxiosResponse<AuthResponseDto>) => void;
  const refreshStarted = new Promise<void>((resolve) => {
    runtime.publicClient.defaults.adapter = (config) => {
      refreshCalls += 1;
      expect(config.url).toBe("/auth/refresh");
      resolve();
      return new Promise((refreshResolve) => {
        resolveRefresh = refreshResolve;
      });
    };
  });
  runtime.authenticatedClient.defaults.adapter = async (config) => {
    const authorization = config.headers.get("Authorization")?.toString();
    if (authorization === "Bearer access-old") {
      oldTokenRequests += 1;
      if (oldTokenRequests === 2) releaseOldTokenFailures!();
      await bothOldTokenRequests;
      throw unauthorized(config);
    }
    return okResponse(config, { authorization, path: config.url });
  };

  const requests = Promise.all([
    runtime.authenticatedClient.get("/books"),
    runtime.authenticatedClient.get("/settings")
  ]);
  await refreshStarted;
  expect(oldTokenRequests).toBe(2);
  expect(refreshCalls).toBe(1);

  resolveRefresh!(okResponse({} as InternalAxiosRequestConfig, refreshedResponse));

  await expect(requests).resolves.toMatchObject([
    { data: { authorization: "Bearer access-new", path: "/books" } },
    { data: { authorization: "Bearer access-new", path: "/settings" } }
  ]);
  expect(refreshCalls).toBe(1);
});
