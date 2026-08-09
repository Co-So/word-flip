import { LocalSettingsStore } from "@/data/http/settings/LocalSettingsStore";

beforeEach(() => {
  window.localStorage.clear();
});

test.each<[string, Storage | null, string?]>([
  ["storage 不可用", null],
  ["无记录", window.localStorage],
  ["JSON 损坏", window.localStorage, "{"],
  ["版本错误", window.localStorage, JSON.stringify({ version: 2, reducedMotion: true })],
  ["version 类型错误", window.localStorage, JSON.stringify({ version: "1", reducedMotion: true })],
  ["reducedMotion 类型错误", window.localStorage, JSON.stringify({ version: 1, reducedMotion: "true" })],
  ["存在额外字段", window.localStorage, JSON.stringify({ version: 1, reducedMotion: true, accessToken: "secret" })]
])("%s 时读取安全回退 false", (_label, storage, record) => {
  if (record !== undefined) {
    window.localStorage.setItem("wordflip.web.settings.v1", record);
  }

  expect(new LocalSettingsStore(storage).read()).toBe(false);
});

test("读取版本正确的设备本地动效偏好", () => {
  window.localStorage.setItem(
    "wordflip.web.settings.v1",
    JSON.stringify({ version: 1, reducedMotion: true })
  );

  expect(new LocalSettingsStore(window.localStorage).read()).toBe(true);
});

test("写入仅保存版本和 reducedMotion，不泄漏服务端偏好或令牌", () => {
  const store = new LocalSettingsStore(window.localStorage);

  store.write(true);

  expect(window.localStorage.length).toBe(1);
  expect(window.localStorage.getItem("wordflip.web.settings.v1")).toBe(
    JSON.stringify({ version: 1, reducedMotion: true })
  );
  expect(window.localStorage.getItem("wordflip.web.settings.v1")).not.toMatch(
    /autoSpeak|groupSize|groupStrategy|token/i
  );
});

test("storage 不可用时写入不抛错", () => {
  expect(() => new LocalSettingsStore(null).write(true)).not.toThrow();
});

test("setItem 抛出 QuotaExceededError 时写入 best-effort 且不抛错", () => {
  const storage: Storage = {
    length: 0,
    clear() {},
    getItem() { return null; },
    key() { return null; },
    removeItem() {},
    setItem() { throw new DOMException("本地存储配额已满", "QuotaExceededError"); }
  };

  expect(() => new LocalSettingsStore(storage).write(true)).not.toThrow();
});
