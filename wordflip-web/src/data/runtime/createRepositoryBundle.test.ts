import { HttpAuthRepository } from "@/data/http/auth/HttpAuthRepository";
import { DemoStateStore } from "@/data/mock/DemoStateStore";
import { createDemoState } from "@/data/mock/createDemoState";
import { MockAuthRepository } from "@/data/mock/repositories/MockAuthRepository";
import { MockBookRepository } from "@/data/mock/repositories/MockBookRepository";
import { MockQuizRepository } from "@/data/mock/repositories/MockQuizRepository";
import { createRepositoryBundle } from "@/data/runtime/createRepositoryBundle";

function createStore() {
  return new DemoStateStore({ initialState: createDemoState("logged-out"), storage: null });
}

beforeEach(() => {
  window.localStorage.clear();
});

test("http 数据源只替换认证 Repository", () => {
  const repositories = createRepositoryBundle({
    dataSource: "http",
    apiBaseUrl: "http://127.0.0.1:8080/api/v1",
    storage: window.localStorage,
    demoStore: createStore()
  });

  expect(repositories.auth).toBeInstanceOf(HttpAuthRepository);
  expect(repositories.books).toBeInstanceOf(MockBookRepository);
  expect(repositories.quiz).toBeInstanceOf(MockQuizRepository);
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
