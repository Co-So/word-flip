import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from "axios";
import { createHttpRuntime } from "@/data/http/createHttpRuntime";
import { HttpSettingsRepository } from "@/data/http/settings/HttpSettingsRepository";
import { LocalSettingsStore } from "@/data/http/settings/LocalSettingsStore";
import type { UserSettingsDto } from "@/data/http/settings/settingsDtos";
import type { LearningPlanDto } from "@/data/http/books/bookDtos";
import { createDemoState } from "@/data/mock/createDemoState";
import { createDemoStateStore } from "@/data/mock/DemoStateStore";
import { MockSettingsRepository } from "@/data/mock/repositories/MockSettingsRepository";

const settingsDto: UserSettingsDto = {
  activePlanId: 101,
  groupSize: 20,
  groupStrategy: "frequency",
  autoSpeak: true,
  themeMode: "system",
  heatDisplayMode: "free",
  quizLaunchMode: "free_select",
  defaultQuestionLimit: 10
};

const planDto: LearningPlanDto = {
  planId: 303,
  bookId: 33,
  bookName: "雅思核心词汇",
  status: "active",
  dailyNewCardLimit: 20,
  active: true,
  createdAt: "2026-08-09T10:00:00Z"
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

function unavailable(config: InternalAxiosRequestConfig): AxiosError {
  const response: AxiosResponse = {
    data: { code: "INTERNAL_ERROR", message: "service unavailable" },
    status: 503,
    statusText: "Service Unavailable",
    headers: new AxiosHeaders(),
    config
  };
  return new AxiosError("request failed", AxiosError.ERR_BAD_RESPONSE, config, undefined, response);
}

function createHarness(adapter: AxiosAdapter, storage: Storage | null = window.localStorage) {
  const runtime = createHttpRuntime({ baseURL: "/api/v1", storage: null });
  runtime.authenticatedClient.defaults.adapter = adapter;
  const localStore = new LocalSettingsStore(storage);
  return {
    localStore,
    repository: new HttpSettingsRepository(runtime.authenticatedClient, localStore)
  };
}

function throwingStorage(): Storage {
  return {
    length: 0,
    clear() {},
    getItem() { return null; },
    key() { return null; },
    removeItem() {},
    setItem() { throw new DOMException("本地存储配额已满", "QuotaExceededError"); }
  };
}

beforeEach(() => {
  window.localStorage.clear();
});

test("GET /settings 映射服务端偏好并合并设备本地 reducedMotion", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const { localStore, repository } = createHarness(async (config) => {
    requests.push(config);
    return okResponse(config, settingsDto);
  });
  localStore.write(true);

  await expect(repository.getSettings()).resolves.toEqual({
    soundEnabled: true,
    reducedMotion: true,
    groupSize: 20,
    groupStrategy: "frequency"
  });
  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatchObject({ method: "get", url: "/settings" });
});

test("PATCH 只发送服务端设置并在成功后保存本地动效偏好", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const responseDto = { ...settingsDto, groupSize: 30 as const, groupStrategy: "random" as const, autoSpeak: false };
  const { localStore, repository } = createHarness(async (config) => {
    requests.push(config);
    return okResponse(config, responseDto);
  });

  await expect(repository.updateSettings({
    soundEnabled: false,
    reducedMotion: true,
    groupSize: 30,
    groupStrategy: "random"
  })).resolves.toEqual({
    soundEnabled: false,
    reducedMotion: true,
    groupSize: 30,
    groupStrategy: "random"
  });

  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatchObject({ method: "patch", url: "/settings/preferences" });
  expect(JSON.parse(String(requests[0].data))).toEqual({
    autoSpeak: false,
    groupSize: 30,
    groupStrategy: "random"
  });
  expect(localStore.read()).toBe(true);
  expect(window.localStorage.getItem("wordflip.web.settings.v1")).toBe(
    JSON.stringify({ version: 1, reducedMotion: true })
  );
});

test("groupStrategy 未提供时 PATCH 省略该字段并采用服务端返回策略", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const responseDto = { ...settingsDto, groupSize: 30 as const, autoSpeak: false };
  const { repository } = createHarness(async (config) => {
    requests.push(config);
    return okResponse(config, responseDto);
  });

  await expect(repository.updateSettings({
    soundEnabled: false,
    reducedMotion: true,
    groupSize: 30
  })).resolves.toEqual({
    soundEnabled: false,
    reducedMotion: true,
    groupSize: 30,
    groupStrategy: "frequency"
  });
  expect(requests).toHaveLength(1);
  expect(JSON.parse(String(requests[0].data))).toEqual({
    autoSpeak: false,
    groupSize: 30
  });
});

test("服务端 PATCH 失败时请求拒绝且本地偏好保持原值", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const { localStore, repository } = createHarness(async (config) => {
    requests.push(config);
    throw unavailable(config);
  });

  await expect(repository.updateSettings({
    soundEnabled: true,
    reducedMotion: true,
    groupSize: 20,
    groupStrategy: "random"
  })).rejects.toMatchObject({ kind: "unavailable" });

  expect(localStore.read()).toBe(false);
  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatchObject({ method: "patch", url: "/settings/preferences" });
});

test("服务端 PATCH 成功但本地写失败时仍返回服务端快照和本次动效偏好", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const { repository } = createHarness(async (config) => {
    requests.push(config);
    return okResponse(config, settingsDto);
  }, throwingStorage());

  await expect(repository.updateSettings({
    soundEnabled: false,
    reducedMotion: true,
    groupSize: 30,
    groupStrategy: "random"
  })).resolves.toEqual({
    soundEnabled: true,
    reducedMotion: true,
    groupSize: 20,
    groupStrategy: "frequency"
  });
  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatchObject({ method: "patch", url: "/settings/preferences" });
});

test("saveOnboarding 先更新分组设置，再以数值 bookId 创建学习计划", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const { repository } = createHarness(async (config) => {
    requests.push(config);
    return config.url === "/settings/preferences"
      ? okResponse(config, { ...settingsDto, groupSize: 30, groupStrategy: "book_order" })
      : okResponse(config, planDto, 201);
  });

  await expect(repository.saveOnboarding({
    bookId: "33",
    groupSize: 30,
    groupStrategy: "book_order"
  })).resolves.toEqual({ planId: "303", bookId: "33", title: "雅思核心词汇" });

  expect(requests.map(({ method, url, data }) => ({
    method,
    url,
    body: JSON.parse(String(data))
  }))).toEqual([
    {
      method: "patch",
      url: "/settings/preferences",
      body: { groupSize: 30, groupStrategy: "book_order" }
    },
    { method: "post", url: "/learning-plans", body: { bookId: 33 } }
  ]);
});

test("onboarding 第二步失败后，重试会以相同顺序和 payload 重做两步", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  let postAttempts = 0;
  const { repository } = createHarness(async (config) => {
    requests.push(config);
    if (config.url === "/learning-plans" && postAttempts++ === 0) throw unavailable(config);
    return config.url === "/learning-plans"
      ? okResponse(config, planDto, 201)
      : okResponse(config, { ...settingsDto, groupSize: 30, groupStrategy: "book_order" });
  });
  const input = { bookId: "33", groupSize: 30 as const, groupStrategy: "book_order" as const };

  await expect(repository.saveOnboarding(input)).rejects.toMatchObject({ kind: "unavailable" });
  await expect(repository.saveOnboarding(input)).resolves.toEqual({
    planId: "303",
    bookId: "33",
    title: "雅思核心词汇"
  });

  expect(requests.map(({ method, url, data }) => ({
    method,
    url,
    body: JSON.parse(String(data))
  }))).toEqual([
    { method: "patch", url: "/settings/preferences", body: { groupSize: 30, groupStrategy: "book_order" } },
    { method: "post", url: "/learning-plans", body: { bookId: 33 } },
    { method: "patch", url: "/settings/preferences", body: { groupSize: 30, groupStrategy: "book_order" } },
    { method: "post", url: "/learning-plans", body: { bookId: 33 } }
  ]);
});

test.each([
  ["前导空白", " 1"],
  ["后缀空白", "1 "],
  ["前导零", "01"],
  ["正号", "+1"],
  ["指数", "1e2"],
  ["十六进制", "0x10"],
  ["超过安全整数", "9007199254740992"],
  ["零", "0"],
  ["负数", "-1"],
  ["小数", "1.5"],
  ["NaN", "NaN"],
  ["Infinity", "Infinity"]
])("onboarding %s bookId %s 在 HTTP 前拒绝", async (_label, bookId) => {
  let requestCount = 0;
  const { repository } = createHarness(async (config) => {
    requestCount += 1;
    return okResponse(config, settingsDto);
  });

  await expect(repository.saveOnboarding({
    bookId,
    groupSize: 20,
    groupStrategy: "book_order"
  })).rejects.toMatchObject({ kind: "validation" });
  expect(requestCount).toBe(0);
});

test("HTTP 不支持重置演示数据，resetDemo 直接拒绝且不发网络", async () => {
  let requestCount = 0;
  const { repository } = createHarness(async (config) => {
    requestCount += 1;
    return okResponse(config, settingsDto);
  });

  expect(repository.supportsDemoReset()).toBe(false);
  await expect(repository.resetDemo()).rejects.toMatchObject({ kind: "validation" });
  expect(requestCount).toBe(0);
});

test("Mock 保持支持重置演示数据", () => {
  const store = createDemoStateStore({ initialState: createDemoState("configured"), storage: null });
  const repository = new MockSettingsRepository(store);

  expect(repository.supportsDemoReset()).toBe(true);
});
