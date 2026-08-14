import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from "axios";
import { createHttpRuntime } from "@/data/http/createHttpRuntime";
import type {
  GroupCardPageDto,
  GroupDetailDto,
  GroupListResponseDto
} from "@/data/http/groups/groupDtos";
import { HttpGroupRepository } from "@/data/http/groups/HttpGroupRepository";

const groupDto: GroupDetailDto = {
  id: 12,
  name: "第 12 组",
  source: "auto",
  status: "learning",
  createdAt: "2026-08-13T08:00:00Z",
  stats: { heat0: 1, heat1: 2, heat2: 3, heat3: 4, heat4: 5, total: 15 },
  progress: 0.4
};

const skillSnapshot = {
  state: "review" as const,
  dueAt: "2026-08-14T08:00:00Z",
  stability: 42.5,
  difficulty: 4.2,
  reps: 6,
  lapses: 1
};

const sourceMaterials = {
  sourceMaterials: [{
    sourceId: "ecdict",
    sourceName: "ECDICT",
    revision: "2026-08",
    senses: [{ pos: "adjective", cn: "可持续的", primary: true }]
  }]
};

const cardsDto: GroupCardPageDto = {
  page: 2,
  size: 20,
  totalElements: 21,
  totalPages: 2,
  cards: [
    {
      ...sourceMaterials,
      cardId: 31,
      lexemeId: 301,
      bookId: 11,
      wordKey: "sustainable",
      en: "sustainable",
      phonetic: "/səˈsteɪnəbl/",
      version: 1,
      senses: [
        { pos: "noun", cn: "持续性", primary: false },
        { pos: "adjective", cn: "可持续的", primary: true }
      ],
      progress: {
        dictation: skillSnapshot,
        choice: { ...skillSnapshot, state: "learning", reps: 2, lapses: 0 },
        displayHeatLevel: 3
      }
    },
    {
      ...sourceMaterials,
      cardId: 32,
      lexemeId: 302,
      bookId: 11,
      wordKey: "resilient",
      en: "resilient",
      phonetic: null,
      version: 1,
      senses: [{ pos: null, cn: "有韧性的", primary: false }],
      progress: {
        dictation: { ...skillSnapshot, state: "new", reps: 0, lapses: 0 },
        choice: { ...skillSnapshot, state: "relearning" },
        displayHeatLevel: 1
      }
    }
  ]
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

function conflictResponse(config: InternalAxiosRequestConfig): AxiosError {
  const response: AxiosResponse = {
    data: { code: "GROUP_MEMBER_CONFLICT", message: "部分学习卡已加入其它分组" },
    status: 409,
    statusText: "Conflict",
    headers: new AxiosHeaders(),
    config
  };
  return new AxiosError("request failed", AxiosError.ERR_BAD_RESPONSE, config, undefined, response);
}

function createHarness(adapter: AxiosAdapter) {
  const runtime = createHttpRuntime({ baseURL: "/api/v1", storage: null });
  runtime.authenticatedClient.defaults.adapter = adapter;
  return new HttpGroupRepository(runtime.authenticatedClient);
}

test("五个公开操作发送精确请求并映射分组、分页与主考义", async () => {
  const requests: InternalAxiosRequestConfig[] = [];
  const repository = createHarness(async (config) => {
    requests.push(config);
    if (config.url === "/groups") {
      return okResponse<GroupListResponseDto>(config, { groups: [groupDto] });
    }
    if (config.url === "/groups/12") return okResponse(config, groupDto);
    if (config.url === "/groups/12/cards") return okResponse(config, cardsDto);
    if (config.url === "/learning/cards/unassigned") return okResponse(config, cardsDto);
    return okResponse(config, { ...groupDto, id: 13, name: "重点", source: "custom" }, 201);
  });
  const input = { name: "  重点  ", cardIds: ["31", "32"] };
  const originalInput = structuredClone(input);

  await expect(repository.listGroups({ source: "auto", sort: "name" })).resolves.toEqual([
    {
      groupId: "12",
      name: "第 12 组",
      source: "auto",
      status: "learning",
      createdAt: "2026-08-13T08:00:00Z",
      stats: { heat0: 1, heat1: 2, heat2: 3, heat3: 4, heat4: 5, total: 15 },
      progress: 0.4
    }
  ]);
  await expect(repository.getDetail("12")).resolves.toMatchObject({ groupId: "12" });
  await expect(repository.listCards("12", 2, 20)).resolves.toMatchObject({
    page: 2,
    size: 20,
    totalElements: 21,
    totalPages: 2,
    cards: [
      expect.objectContaining({
        cardId: "31",
        lexemeId: "301",
        primaryPos: "adjective",
        primaryDefinition: "可持续的",
        displayHeatLevel: 3
      }),
      expect.objectContaining({
        cardId: "32",
        primaryPos: null,
        primaryDefinition: "有韧性的"
      })
    ]
  });
  await expect(repository.listUnassigned({ all: true, q: "sust", page: 1, size: 100 }))
    .resolves.toMatchObject({ cards: expect.any(Array) });
  await expect(repository.createCustomGroup(input)).resolves.toMatchObject({
    groupId: "13",
    name: "重点",
    source: "custom"
  });

  expect(input).toEqual(originalInput);
  expect(requests[0]).toMatchObject({
    method: "get",
    url: "/groups",
    params: { source: "auto", sort: "name" }
  });
  expect(requests[1]).toMatchObject({ method: "get", url: "/groups/12" });
  expect(requests[2]).toMatchObject({
    method: "get",
    url: "/groups/12/cards",
    params: { page: 2, size: 20 }
  });
  expect(requests[3]).toMatchObject({
    method: "get",
    url: "/learning/cards/unassigned",
    params: { all: true, q: "sust", page: 1, size: 100 }
  });
  expect(requests[4]).toMatchObject({ method: "post", url: "/groups/custom" });
  expect(JSON.parse(String(requests[4].data))).toEqual({ name: "重点", cardIds: [31, 32] });
});

test.each([
  ["前导空白", " 1"],
  ["后缀空白", "1 "],
  ["前导零", "01"],
  ["指数", "1e2"],
  ["超过安全整数", "9007199254740992"],
  ["零", "0"],
  ["负数", "-1"],
  ["小数", "1.5"]
])("%s groupId %s 在所有入口的 HTTP 前返回 validation", async (_label, groupId) => {
  let requestCount = 0;
  const repository = createHarness(async (config) => {
    requestCount += 1;
    return okResponse(config, {});
  });

  const results = await Promise.allSettled([
    repository.getDetail(groupId),
    repository.listCards(groupId)
  ]);

  expect(requestCount).toBe(0);
  for (const result of results) {
    expect(result).toEqual({
      status: "rejected",
      reason: expect.objectContaining({ kind: "validation" })
    });
  }
});

test.each([
  ["空选择", []],
  ["重复 ID", ["31", "31"]],
  ["非法 ID", ["31", "01"]],
  ["超过 500 个", Array.from({ length: 501 }, (_, index) => String(index + 1))]
])("createCustomGroup 拒绝%s且不发送 HTTP", async (_label, cardIds) => {
  let requestCount = 0;
  const repository = createHarness(async (config) => {
    requestCount += 1;
    return okResponse(config, groupDto);
  });
  const input = { name: "  重点  ", cardIds };
  const originalInput = structuredClone(input);

  await expect(repository.createCustomGroup(input)).rejects.toEqual(
    expect.objectContaining({ kind: "validation" })
  );
  expect(requestCount).toBe(0);
  expect(input).toEqual(originalInput);
});

test("createCustomGroup 保留共享客户端映射的 409 conflict 且不突变输入", async () => {
  const repository = createHarness(async (config) => {
    throw conflictResponse(config);
  });
  const input = { name: "  重点  ", cardIds: ["31", "32"] };
  const originalInput = structuredClone(input);

  await expect(repository.createCustomGroup(input)).rejects.toEqual({
    kind: "conflict",
    message: "部分学习卡已加入其它分组"
  });
  expect(input).toEqual(originalInput);
});

test.each([
  ["缺少 groups", {}],
  ["缺少 stats", { groups: [{ ...groupDto, stats: undefined }] }],
  ["非法来源枚举", { groups: [{ ...groupDto, source: "manual" }] }],
  ["非法状态枚举", { groups: [{ ...groupDto, status: "done" }] }],
  ["热力统计为小数", { groups: [{ ...groupDto, stats: { ...groupDto.stats, heat2: 1.5 } }] }],
  ["进度超过百分比范围", { groups: [{ ...groupDto, progress: 1.01 }] }]
])("分组响应%s时抛出稳定的数据不完整错误", async (_label, payload) => {
  const repository = createHarness(async (config) => okResponse(config, payload));

  await expect(repository.listGroups()).rejects.toEqual({
    kind: "unknown",
    message: "分组接口返回数据不完整"
  });
});

test.each([
  ["缺少 cards", { ...cardsDto, cards: undefined }, "分组学习卡接口返回数据不完整"],
  ["卡片缺少 senses", { ...cardsDto, cards: [{ ...cardsDto.cards[0], senses: undefined }] }, "分组学习卡接口返回数据不完整"],
  ["卡片缺少 sourceMaterials", {
    ...cardsDto,
    cards: [{ ...cardsDto.cards[0], sourceMaterials: undefined }]
  }, "分组学习卡接口返回数据不完整"],
  ["sourceMaterials 条目缺少 revision", {
    ...cardsDto,
    cards: [{
      ...cardsDto.cards[0],
      sourceMaterials: [{ sourceId: "ecdict", sourceName: "ECDICT", senses: [] }]
    }]
  }, "分组学习卡接口返回数据不完整"],
  ["卡片没有考义", { ...cardsDto, cards: [{ ...cardsDto.cards[0], senses: [] }] }, "学习卡缺少考义"],
  ["主考义缺少中文", {
    ...cardsDto,
    cards: [{ ...cardsDto.cards[0], senses: [{ pos: "adjective", cn: null, primary: true }] }]
  }, "学习卡考义缺少中文释义"],
  ["displayHeatLevel 越界", {
    ...cardsDto,
    cards: [{
      ...cardsDto.cards[0],
      progress: { ...cardsDto.cards[0].progress, displayHeatLevel: 5 }
    }]
  }, "分组学习卡接口返回数据不完整"],
  ["FSRS reps 为小数", {
    ...cardsDto,
    cards: [{
      ...cardsDto.cards[0],
      progress: {
        ...cardsDto.cards[0].progress,
        dictation: { ...cardsDto.cards[0].progress.dictation, reps: 1.5 }
      }
    }]
  }, "分组学习卡接口返回数据不完整"]
])("分页学习卡响应%s时不泄漏 TypeError", async (_label, payload, message) => {
  const repository = createHarness(async (config) => okResponse(config, payload));

  await expect(repository.listCards("12")).rejects.toEqual({
    kind: "unknown",
    message
  });
});
