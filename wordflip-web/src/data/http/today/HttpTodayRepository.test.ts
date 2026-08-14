import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from "axios";
import { createHttpRuntime } from "@/data/http/createHttpRuntime";
import type { TodayDashboardDto } from "@/data/http/today/todayDtos";
import { HttpTodayRepository } from "@/data/http/today/HttpTodayRepository";

const dashboardDto: TodayDashboardDto = {
  date: "2026-08-13",
  streakDays: 7,
  stats: { masteredCount: 12, dueReviewCount: 4, completionPercent: 60 },
  tasks: {
    newWords: {
      count: 10,
      label: "新词",
      sources: [{ groupId: 91, groupName: "第 1 组", count: 10 }]
    },
    dueReview: {
      count: 4,
      label: "到期复习",
      sources: [{ groupId: 92, groupName: "第 2 组", count: 4 }]
    },
    quiz: { count: 6, label: "测验" }
  },
  recommendedStudy: {
    groupId: 91,
    groupName: "第 1 组",
    wordCount: 20,
    reason: "mixed"
  },
  recentGroups: [
    { groupId: 91, name: "第 1 组", lastStudiedAt: "2026-08-13T08:00:00Z" }
  ]
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

function responseError(config: InternalAxiosRequestConfig): AxiosError {
  const response: AxiosResponse = {
    data: { code: "NOT_FOUND", message: "当前计划不存在" },
    status: 404,
    statusText: "Not Found",
    headers: new AxiosHeaders(),
    config
  };
  return new AxiosError("request failed", AxiosError.ERR_BAD_RESPONSE, config, undefined, response);
}

function createHarness(adapter: AxiosAdapter) {
  const runtime = createHttpRuntime({ baseURL: "/api/v1", storage: null });
  runtime.authenticatedClient.defaults.adapter = adapter;
  return new HttpTodayRepository(runtime.authenticatedClient, () => "Asia/Shanghai");
}

test("getSummary 携带时区请求 Today 并将所有数值 groupId 归一化为字符串", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const repository = createHarness(async (config) => {
    requests.push(config);
    return okResponse(config, dashboardDto);
  });

  await expect(repository.getSummary()).resolves.toEqual({
    date: "2026-08-13",
    streakDays: 7,
    stats: { masteredCount: 12, dueReviewCount: 4, completionPercent: 60 },
    tasks: {
      newWords: {
        count: 10,
        label: "新词",
        sources: [{ groupId: "91", groupName: "第 1 组", count: 10 }]
      },
      dueReview: {
        count: 4,
        label: "到期复习",
        sources: [{ groupId: "92", groupName: "第 2 组", count: 4 }]
      },
      quiz: { count: 6, label: "测验", sources: [] }
    },
    recommendedStudy: {
      groupId: "91",
      groupName: "第 1 组",
      wordCount: 20,
      reason: "mixed"
    },
    recentGroups: [
      { groupId: "91", name: "第 1 组", lastStudiedAt: "2026-08-13T08:00:00Z" }
    ]
  });
  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatchObject({ method: "get", url: "/today" });
  expect(requests[0].headers.get("X-Timezone")).toBe("Asia/Shanghai");
});

test("recommendedStudy 为 null 时原样保留", async () => {
  const repository = createHarness(async (config) =>
    okResponse(config, { ...dashboardDto, recommendedStudy: null })
  );

  await expect(repository.getSummary()).resolves.toMatchObject({ recommendedStudy: null });
});

test.each([
  ["缺少 stats", { ...dashboardDto, stats: undefined }],
  ["缺少 tasks", { ...dashboardDto, tasks: undefined }],
  ["缺少 newWords", { ...dashboardDto, tasks: { ...dashboardDto.tasks, newWords: undefined } }],
  ["缺少 recentGroups", { ...dashboardDto, recentGroups: undefined }]
])("%s 时抛出具体的数据不完整错误", async (_label, payload) => {
  const repository = createHarness(async (config) => okResponse(config, payload));

  await expect(repository.getSummary()).rejects.toEqual({
    kind: "unknown",
    message: "今日接口返回数据不完整"
  });
});

test("共享客户端已映射的 Axios 错误不被仓储二次包装", async () => {
  const repository = createHarness(async (config) => {
    throw responseError(config);
  });

  await expect(repository.getSummary()).rejects.toEqual({
    kind: "not-found",
    message: "当前计划不存在"
  });
});
