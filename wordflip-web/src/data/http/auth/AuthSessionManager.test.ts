import { AuthSessionManager } from "@/data/http/auth/AuthSessionManager";
import type { AuthResponseDto } from "@/data/http/auth/authDtos";
import { TokenStore } from "@/data/http/auth/TokenStore";

const initialResponse: AuthResponseDto = {
  accessToken: "access-old",
  refreshToken: "refresh-old",
  expiresIn: 60,
  user: { id: 42, email: "linmo@example.test", phone: null }
};

const refreshedResponse: AuthResponseDto = {
  accessToken: "access-new",
  refreshToken: "refresh-new",
  expiresIn: 120,
  user: { id: 42, email: "linmo@example.test", phone: null }
};

beforeEach(() => {
  window.localStorage.clear();
});

test("建立会话时持久化完整令牌并从服务端用户摘要映射页面会话", async () => {
  const store = new TokenStore(window.localStorage);
  const manager = new AuthSessionManager(store, async () => refreshedResponse, () => 1_000);

  expect(manager.startSession(initialResponse)).toEqual({
    userId: "42",
    displayName: "linmo@example.test",
    authenticated: true
  });
  expect(store.read()).toEqual({
    schemaVersion: 1,
    accessToken: "access-old",
    refreshToken: "refresh-old",
    accessExpiresAt: 61_000,
    user: initialResponse.user
  });
  await expect(manager.getSession()).resolves.toMatchObject({ userId: "42" });
});

test("用户没有邮箱时依次使用手机号和用户编号作为展示标签", () => {
  const store = new TokenStore(window.localStorage);
  const manager = new AuthSessionManager(store, async () => refreshedResponse, () => 1_000);

  expect(manager.startSession({
    ...initialResponse,
    user: { id: 43, email: null, phone: "+8613800138000" }
  }).displayName).toBe("+8613800138000");
  expect(manager.startSession({
    ...initialResponse,
    user: { id: 44, email: null, phone: null }
  }).displayName).toBe("用户 44");
});

test("Access Token 到期时刷新并原子替换完整令牌记录", async () => {
  const store = new TokenStore(window.localStorage);
  const refreshTokens = vi.fn(async (refreshToken: string) => {
    expect(refreshToken).toBe("refresh-old");
    return refreshedResponse;
  });
  const manager = new AuthSessionManager(store, refreshTokens, () => 100_000);
  manager.startSession({ ...initialResponse, expiresIn: 0 });

  await expect(manager.getSession()).resolves.toMatchObject({ userId: "42" });
  expect(refreshTokens).toHaveBeenCalledTimes(1);
  expect(store.read()).toMatchObject({
    accessToken: "access-new",
    refreshToken: "refresh-new",
    accessExpiresAt: 220_000
  });
});

test("并发刷新只轮换一次令牌并让所有调用获得新 Access Token", async () => {
  const store = new TokenStore(window.localStorage);
  let refreshCalls = 0;
  const manager = new AuthSessionManager(store, async () => {
    refreshCalls += 1;
    return refreshedResponse;
  }, () => 1_000);
  manager.startSession(initialResponse);

  await expect(Promise.all([
    manager.refreshAccessToken(),
    manager.refreshAccessToken()
  ])).resolves.toEqual(["access-new", "access-new"]);
  expect(refreshCalls).toBe(1);
});

test("刷新失败清理本地令牌，恢复会话返回未登录", async () => {
  const store = new TokenStore(window.localStorage);
  const manager = new AuthSessionManager(store, async () => {
    throw new Error("refresh rejected");
  }, () => 100_000);
  manager.startSession({ ...initialResponse, expiresIn: 0 });

  await expect(manager.getSession()).resolves.toBeNull();
  expect(store.read()).toBeNull();
  expect(manager.getAccessToken()).toBeNull();
});
