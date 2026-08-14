import { HttpAuthRepository } from "@/data/http/auth/HttpAuthRepository";
import { HttpBookRepository } from "@/data/http/books/HttpBookRepository";
import { createHttpRuntime } from "@/data/http/createHttpRuntime";
import { HttpSettingsRepository } from "@/data/http/settings/HttpSettingsRepository";
import { HttpTodayRepository } from "@/data/http/today/HttpTodayRepository";
import { DemoStateStore } from "@/data/mock/DemoStateStore";
import { createDemoState } from "@/data/mock/createDemoState";
import { MockAuthRepository } from "@/data/mock/repositories/MockAuthRepository";
import { MockBookRepository } from "@/data/mock/repositories/MockBookRepository";
import { MockGroupRepository } from "@/data/mock/repositories/MockGroupRepository";
import { MockMediaRepository } from "@/data/mock/repositories/MockMediaRepository";
import { MockQuizRepository } from "@/data/mock/repositories/MockQuizRepository";
import { MockSettingsRepository } from "@/data/mock/repositories/MockSettingsRepository";
import { MockStatsRepository } from "@/data/mock/repositories/MockStatsRepository";
import { MockStudyRepository } from "@/data/mock/repositories/MockStudyRepository";
import { createRepositoryBundle } from "@/data/runtime/createRepositoryBundle";
import { vi } from "vitest";

vi.mock("@/data/http/createHttpRuntime", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/data/http/createHttpRuntime")>();
  return { ...actual, createHttpRuntime: vi.fn(actual.createHttpRuntime) };
});

function createStore() {
  return new DemoStateStore({ initialState: createDemoState("logged-out"), storage: null });
}

beforeEach(() => {
  window.localStorage.clear();
  vi.mocked(createHttpRuntime).mockClear();
});

test("http 数据源以单一运行时替换认证、词书、今日和设置 Repository", () => {
  const repositories = createRepositoryBundle({
    dataSource: "http",
    apiBaseUrl: "  http://127.0.0.1:8080/api/v1  ",
    storage: window.localStorage,
    demoStore: createStore()
  });
  const runtime = vi.mocked(createHttpRuntime).mock.results[0]?.value;

  expect(createHttpRuntime).toHaveBeenCalledOnce();
  expect(createHttpRuntime).toHaveBeenCalledWith({
    baseURL: "http://127.0.0.1:8080/api/v1",
    storage: window.localStorage
  });
  expect(repositories.auth).toBeInstanceOf(HttpAuthRepository);
  expect(repositories.books).toBeInstanceOf(HttpBookRepository);
  expect(repositories.settings).toBeInstanceOf(HttpSettingsRepository);
  expect(repositories.groups).toBeInstanceOf(MockGroupRepository);
  expect(repositories.today).toBeInstanceOf(HttpTodayRepository);
  expect(repositories.study).toBeInstanceOf(MockStudyRepository);
  expect(repositories.quiz).toBeInstanceOf(MockQuizRepository);
  expect(repositories.media).toBeInstanceOf(MockMediaRepository);
  expect(repositories.stats).toBeInstanceOf(MockStatsRepository);
  expect((repositories.auth as unknown as { publicClient: unknown }).publicClient)
    .toBe(runtime?.publicClient);
  expect((repositories.auth as unknown as { authenticatedClient: unknown }).authenticatedClient)
    .toBe(runtime?.authenticatedClient);
  expect((repositories.auth as unknown as { sessions: unknown }).sessions).toBe(runtime?.sessions);
  expect((repositories.books as unknown as { client: unknown }).client)
    .toBe(runtime?.authenticatedClient);
  expect((repositories.settings as unknown as { client: unknown }).client)
    .toBe(runtime?.authenticatedClient);
  expect((repositories.today as unknown as { client: unknown }).client)
    .toBe(runtime?.authenticatedClient);
});

test.each([undefined, "", "mock", "unexpected"])(
  "数据源 %s 保持完整 Mock Bundle",
  (dataSource) => {
    const repositories = createRepositoryBundle({
      dataSource,
      apiBaseUrl: undefined,
      storage: window.localStorage,
      demoStore: createStore()
    });

    expect(repositories.auth).toBeInstanceOf(MockAuthRepository);
    expect(repositories.books).toBeInstanceOf(MockBookRepository);
    expect(repositories.settings).toBeInstanceOf(MockSettingsRepository);
    expect(createHttpRuntime).not.toHaveBeenCalled();
  }
);

test("http 数据源缺少 API 根地址时给出明确配置错误", () => {
  expect(() => createRepositoryBundle({
    dataSource: "http",
    apiBaseUrl: " ",
    storage: window.localStorage,
    demoStore: createStore()
  })).toThrow("VITE_API_BASE_URL");
});
