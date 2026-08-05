import { AUTH_STORAGE_KEY, TokenStore, type StoredAuthRecord } from "@/data/http/auth/TokenStore";

const storedRecord: StoredAuthRecord = {
  schemaVersion: 1,
  accessToken: "access-old",
  refreshToken: "refresh-old",
  accessExpiresAt: 2_000,
  user: { id: 42, email: "linmo@example.test", phone: null }
};

beforeEach(() => {
  window.localStorage.clear();
});

test("完整令牌记录可以原样保存、恢复和清空", () => {
  const store = new TokenStore(window.localStorage);

  store.write(storedRecord);
  expect(store.read()).toEqual(storedRecord);

  store.clear();
  expect(store.read()).toBeNull();
});

test.each([
  "不是 JSON",
  JSON.stringify({ schemaVersion: 0 }),
  JSON.stringify({ ...storedRecord, accessToken: "" }),
  JSON.stringify({ ...storedRecord, accessExpiresAt: -1 }),
  JSON.stringify({ ...storedRecord, user: { id: "42", email: null, phone: null } })
])("损坏或旧版本令牌记录按未登录处理并从存储移除", (persisted) => {
  window.localStorage.setItem(AUTH_STORAGE_KEY, persisted);
  const store = new TokenStore(window.localStorage);

  expect(store.read()).toBeNull();
  expect(window.localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
});

test("没有浏览器存储时保持未登录且写入与清理不会抛错", () => {
  const store = new TokenStore(null);

  expect(store.read()).toBeNull();
  expect(() => store.write(storedRecord)).not.toThrow();
  expect(() => store.clear()).not.toThrow();
});
