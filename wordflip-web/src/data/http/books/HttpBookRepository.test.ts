import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from "axios";
import type { BookItemDto, LearningPlanDto } from "@/data/http/books/bookDtos";
import { HttpBookRepository } from "@/data/http/books/HttpBookRepository";
import { createHttpRuntime } from "@/data/http/createHttpRuntime";

const currentBook: BookItemDto = {
  id: 11,
  name: "核心词汇",
  source: "builtin",
  wordCount: 300,
  declaredCount: 300,
  selected: true,
  canDelete: false,
  planId: 101,
  planStatus: "paused",
  progress: { masteredCount: 126, assignedCardCount: 300, completionPercent: 42 }
};

const historyBook: BookItemDto = {
  id: 22,
  name: "进阶词汇",
  source: "builtin",
  wordCount: 180,
  declaredCount: null,
  selected: false,
  canDelete: false,
  planId: 202,
  planStatus: "active",
  progress: { masteredCount: 42, assignedCardCount: 180, completionPercent: 23 }
};

const availableBook: BookItemDto = {
  id: 33,
  name: "雅思核心词汇",
  source: "imported",
  wordCount: 3_000,
  declaredCount: 3_000,
  selected: false,
  canDelete: true,
  planId: null,
  planStatus: null,
  progress: null
};

const planDto: LearningPlanDto = {
  planId: 101,
  bookId: 11,
  bookName: "核心词汇",
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

function responseError(
  config: InternalAxiosRequestConfig,
  status: number,
  message: string
): AxiosError {
  const response: AxiosResponse = {
    data: { code: status === 404 ? "NOT_FOUND" : "ERROR", message },
    status,
    statusText: String(status),
    headers: new AxiosHeaders(),
    config
  };
  return new AxiosError("request failed", AxiosError.ERR_BAD_RESPONSE, config, undefined, response);
}

function createHarness(adapter: AxiosAdapter) {
  const runtime = createHttpRuntime({ baseURL: "/api/v1", storage: null });
  runtime.authenticatedClient.defaults.adapter = adapter;
  return new HttpBookRepository(runtime.authenticatedClient);
}

test("list 只按 selected 与 planId 映射三态，不受交叉的 planStatus 影响", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const repository = createHarness(async (config) => {
    requests.push(config);
    return okResponse(config, { books: [currentBook, historyBook, availableBook] });
  });

  await expect(repository.list()).resolves.toEqual([
    {
      bookId: "11",
      title: "核心词汇",
      cardCount: 300,
      planId: "101",
      planStatus: "current",
      progress: { masteredCount: 126, assignedCardCount: 300, completionPercent: 42 }
    },
    {
      bookId: "22",
      title: "进阶词汇",
      cardCount: 180,
      planId: "202",
      planStatus: "history",
      progress: { masteredCount: 42, assignedCardCount: 180, completionPercent: 23 }
    },
    {
      bookId: "33",
      title: "雅思核心词汇",
      cardCount: 3_000,
      planId: null,
      planStatus: "available",
      progress: null
    }
  ]);
  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatchObject({ method: "get", url: "/books" });
});

test("listBooks 复用 GET /books 并只返回首次设置需要的基础字段", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const repository = createHarness(async (config) => {
    requests.push(config);
    return okResponse(config, { books: [currentBook, availableBook] });
  });

  await expect(repository.listBooks()).resolves.toEqual([
    { bookId: "11", title: "核心词汇", cardCount: 300 },
    { bookId: "33", title: "雅思核心词汇", cardCount: 3_000 }
  ]);
  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatchObject({ method: "get", url: "/books" });
});

test("getDetail 请求数字化路径并映射单本词书", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const repository = createHarness(async (config) => {
    requests.push(config);
    return okResponse(config, historyBook);
  });

  await expect(repository.getDetail("22")).resolves.toEqual({
    bookId: "22",
    title: "进阶词汇",
    cardCount: 180,
    planId: "202",
    planStatus: "history",
    progress: { masteredCount: 42, assignedCardCount: 180, completionPercent: 23 }
  });
  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatchObject({ method: "get", url: "/books/22" });
});

test("getActivePlan 请求 current 端点并把数字 ID 转回字符串", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const repository = createHarness(async (config) => {
    requests.push(config);
    return okResponse(config, planDto);
  });

  await expect(repository.getActivePlan()).resolves.toEqual({
    planId: "101",
    bookId: "11",
    title: "核心词汇"
  });
  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatchObject({ method: "get", url: "/learning-plans/current" });
});

test("只有 current-plan 的 404 返回 null，词书详情 404 保持 not-found 拒绝", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const repository = createHarness(async (config) => {
    requests.push(config);
    throw responseError(config, 404, "找不到资源");
  });

  await expect(repository.getActivePlan()).resolves.toBeNull();
  await expect(repository.getDetail("22")).rejects.toEqual({
    kind: "not-found",
    message: "找不到资源"
  });
  expect(requests.map(({ method, url }) => ({ method, url }))).toEqual([
    { method: "get", url: "/learning-plans/current" },
    { method: "get", url: "/books/22" }
  ]);
});

test("activateBook 发送数值 bookId 并映射创建后的计划", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const repository = createHarness(async (config) => {
    requests.push(config);
    return okResponse(config, planDto, 201);
  });

  await expect(repository.activateBook("11")).resolves.toEqual({
    planId: "101",
    bookId: "11",
    title: "核心词汇"
  });
  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatchObject({ method: "post", url: "/learning-plans" });
  expect(JSON.parse(String(requests[0].data))).toEqual({ bookId: 11 });
});

test("switchActivePlan 发送数值 planId 并映射切换后的计划", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const repository = createHarness(async (config) => {
    requests.push(config);
    return okResponse(config, planDto);
  });

  await expect(repository.switchActivePlan("101")).resolves.toEqual({
    planId: "101",
    bookId: "11",
    title: "核心词汇"
  });
  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatchObject({ method: "patch", url: "/learning-plans/current" });
  expect(JSON.parse(String(requests[0].data))).toEqual({ planId: 101 });
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
])("%s ID %s 在所有入口的 HTTP 前返回 validation AppError", async (_label, id) => {
  let requestCount = 0;
  const repository = createHarness(async (config) => {
    requestCount += 1;
    return okResponse(config, {});
  });

  const results = await Promise.allSettled([
    repository.getDetail(id),
    repository.activateBook(id),
    repository.switchActivePlan(id)
  ]);

  expect(requestCount).toBe(0);
  for (const result of results) {
    expect(result).toEqual({
      status: "rejected",
      reason: expect.objectContaining({ kind: "validation" })
    });
  }
});
