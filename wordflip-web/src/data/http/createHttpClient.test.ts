import {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from "axios";
import { AuthSessionManager } from "@/data/http/auth/AuthSessionManager";
import type { AuthResponseDto } from "@/data/http/auth/authDtos";
import { TokenStore } from "@/data/http/auth/TokenStore";
import {
  createAuthenticatedHttpClient,
  createPublicHttpClient
} from "@/data/http/createHttpClient";

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

function createSessions(refresh: (token: string) => Promise<AuthResponseDto>) {
  const tokens = new TokenStore(window.localStorage);
  const sessions = new AuthSessionManager(tokens, refresh, () => 1_000);
  sessions.startSession(initialResponse);
  return { sessions, tokens };
}

beforeEach(() => {
  window.localStorage.clear();
});

test("401 后刷新一次并以新 Bearer Token 重放原请求", async () => {
  const seenAuthorization: Array<string | undefined> = [];
  const { sessions } = createSessions(async () => refreshedResponse);
  const client = createAuthenticatedHttpClient({ baseURL: "/api/v1", sessions });
  client.defaults.adapter = async (config) => {
    seenAuthorization.push(config.headers.get("Authorization")?.toString());
    if (seenAuthorization.length === 1) throw unauthorized(config);
    return okResponse(config, { value: "ok" });
  };

  await expect(client.get("/settings")).resolves.toMatchObject({ data: { value: "ok" } });
  expect(seenAuthorization).toEqual(["Bearer access-old", "Bearer access-new"]);
});

test("重放请求再次 401 时不循环并清理本地会话", async () => {
  let requests = 0;
  const { sessions, tokens } = createSessions(async () => refreshedResponse);
  const client = createAuthenticatedHttpClient({ baseURL: "/api/v1", sessions });
  client.defaults.adapter = async (config) => {
    requests += 1;
    throw unauthorized(config);
  };

  await expect(client.get("/settings")).rejects.toEqual({
    kind: "unauthorized",
    message: "Authentication required"
  });
  expect(requests).toBe(2);
  expect(tokens.read()).toBeNull();
});

test("两个并发 401 共享一次 Refresh Token 轮换", async () => {
  let refreshCalls = 0;
  const { sessions } = createSessions(async () => {
    refreshCalls += 1;
    return refreshedResponse;
  });
  const client = createAuthenticatedHttpClient({ baseURL: "/api/v1", sessions });
  client.defaults.adapter = async (config) => {
    const authorization = config.headers.get("Authorization")?.toString();
    if (authorization === "Bearer access-old") throw unauthorized(config);
    return okResponse(config, { authorization });
  };

  await expect(Promise.all([
    client.get("/settings"),
    client.get("/books")
  ])).resolves.toMatchObject([
    { data: { authorization: "Bearer access-new" } },
    { data: { authorization: "Bearer access-new" } }
  ]);
  expect(refreshCalls).toBe(1);
});

test("公开客户端把最终 HTTP 错误转换为 AppError", async () => {
  const client = createPublicHttpClient("/api/v1");
  client.defaults.adapter = async (config) => {
    throw unauthorized(config);
  };

  await expect(client.post("/auth/login", {})).rejects.toEqual({
    kind: "unauthorized",
    message: "Authentication required"
  });
});
