import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useNavigate } from "react-router-dom";
import { App } from "@/app/App";
import { AppProviders } from "@/app/AppProviders";
import type { AppError } from "@/data/contracts/AppError";
import { DemoStateStore } from "@/data/mock/DemoStateStore";
import { createDemoState } from "@/data/mock/createDemoState";
import { createMockRepositoryBundle } from "@/data/mock/fixtures";
import { RepositoryProvider } from "@/data/runtime/RepositoryContext";
import type {
  FsrsSkillSnapshot,
  GroupCard,
  GroupCardPage,
  GroupRepository,
  WordGroup
} from "@/domain/groups";
import { renderAuthenticatedApp } from "@/test/renderApp";

const group: WordGroup = {
  groupId: "group-12",
  name: "第 12 组 · 城市与环境",
  source: "auto",
  status: "learning",
  createdAt: "2026-07-23T00:00:00Z",
  stats: { heat0: 0, heat1: 0, heat2: 1, heat3: 0, heat4: 0, total: 1 },
  progress: 0.25
};

const newSkill: FsrsSkillSnapshot = {
  state: "new",
  dueAt: "2026-07-23T00:00:00Z",
  stability: 1,
  difficulty: 5,
  reps: 0,
  lapses: 0
};

function card(cardId: string, headword: string, definition: string): GroupCard {
  return {
    cardId,
    lexemeId: `lexeme-${cardId}`,
    headword,
    phonetic: null,
    primaryPos: "adjective",
    primaryDefinition: definition,
    displayHeatLevel: 0,
    progress: { dictation: { ...newSkill }, choice: { ...newSkill } }
  };
}

function page(cards: GroupCard[], currentPage = 1, totalPages = cards.length === 0 ? 0 : 1): GroupCardPage {
  return {
    page: currentPage,
    size: 20,
    totalElements: totalPages > 1 ? 21 : cards.length,
    totalPages,
    cards
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((nextResolve) => { resolve = nextResolve; });
  return { promise, resolve };
}

function NavigationBridge({ onReady }: { onReady: (navigate: ReturnType<typeof useNavigate>) => void }) {
  onReady(useNavigate());
  return null;
}

function renderWithGroups(route: string, groups: GroupRepository) {
  const store = new DemoStateStore({ initialState: createDemoState("configured"), storage: null });
  const repositories = createMockRepositoryBundle(store);
  repositories.groups = groups;
  let navigate: ReturnType<typeof useNavigate> | null = null;
  const view = render(
    <AppProviders>
      <RepositoryProvider repositories={repositories}>
        <MemoryRouter initialEntries={[route]} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
          <NavigationBridge onReady={(nextNavigate) => { navigate = nextNavigate; }} />
          <App />
        </MemoryRouter>
      </RepositoryProvider>
    </AppProviders>
  );
  return {
    ...view,
    navigate: async (to: string) => {
      await act(async () => { navigate?.(to); });
    }
  };
}

test("分组卡展示来源、状态、五档热力与服务端进度", async () => {
  renderAuthenticatedApp("/groups");

  const item = await screen.findByRole("article", { name: "第 12 组 · 城市与环境" });
  expect(within(item).getByText("自动分组")).toBeVisible();
  expect(within(item).getByText("学习中")).toBeVisible();
  expect(within(item).getByText("已掌握 25%")).toBeVisible();
  expect(within(item).getAllByText(/热力 [0-4]/)).toHaveLength(5);
  expect(within(item).getByRole("link", { name: "查看分组" })).toHaveAttribute("href", "/groups/group-12");
  expect(screen.getByRole("link", { name: "新建自定义分组" })).toHaveAttribute("href", "/groups/new");
});

test("详情分页卡片只读展示服务端双轨快照", async () => {
  const sustainable = card("card-sustainable", "sustainable", "可持续的");
  sustainable.displayHeatLevel = 2;
  sustainable.progress.dictation = { ...newSkill, state: "learning", stability: 12 };
  const listCards = vi.fn<GroupRepository["listCards"]>().mockResolvedValue(page([sustainable]));
  renderWithGroups("/groups/group-12", {
    listGroups: vi.fn().mockResolvedValue([group]),
    getDetail: vi.fn().mockResolvedValue(group),
    listCards,
    listUnassigned: vi.fn().mockResolvedValue(page([])),
    createCustomGroup: vi.fn().mockResolvedValue(group)
  });

  const row = await screen.findByRole("row", { name: /sustainable/ });
  expect(within(row).getByText("默写")).toBeVisible();
  expect(within(row).getByText("选择")).toBeVisible();
  expect(within(row).getByText("learning · S 12")).toBeVisible();
  expect(within(row).getByText("new · S 1 · 热力 2")).toBeVisible();
  expect(within(row).queryByRole("button", { name: /记得|模糊|不认识/ })).not.toBeInTheDocument();
  expect(listCards).toHaveBeenCalledWith("group-12", 1, 20);
});

test("详情分页只在边界内展示上一页与下一页", async () => {
  const user = userEvent.setup();
  const first = card("card-first", "first", "第一张");
  const second = card("card-second", "second", "第二张");
  const listCards = vi.fn<GroupRepository["listCards"]>((_groupId, requestedPage = 1) =>
    Promise.resolve(page(requestedPage === 1 ? [first] : [second], requestedPage, 2))
  );
  renderWithGroups("/groups/group-12", {
    listGroups: vi.fn().mockResolvedValue([group]),
    getDetail: vi.fn().mockResolvedValue(group),
    listCards,
    listUnassigned: vi.fn().mockResolvedValue(page([])),
    createCustomGroup: vi.fn().mockResolvedValue(group)
  });

  expect(await screen.findByRole("row", { name: /first/ })).toBeVisible();
  expect(screen.queryByRole("button", { name: "上一页" })).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "下一页" }));
  expect(await screen.findByRole("row", { name: /second/ })).toBeVisible();
  expect(screen.getByRole("button", { name: "上一页" })).toBeVisible();
  expect(screen.queryByRole("button", { name: "下一页" })).not.toBeInTheDocument();
  expect(listCards).toHaveBeenLastCalledWith("group-12", 2, 20);
});

test("服务端总页数收缩时自动回到最后有效页", async () => {
  const user = userEvent.setup();
  const first = card("card-first", "first", "恢复后的第一页");
  let firstPageRequests = 0;
  const listCards = vi.fn<GroupRepository["listCards"]>((_groupId, requestedPage = 1) => {
    if (requestedPage === 2) return Promise.resolve(page([], 2, 1));
    firstPageRequests += 1;
    return Promise.resolve(page([first], 1, firstPageRequests === 1 ? 2 : 1));
  });
  renderWithGroups("/groups/group-12", {
    listGroups: vi.fn().mockResolvedValue([group]),
    getDetail: vi.fn().mockResolvedValue(group),
    listCards,
    listUnassigned: vi.fn().mockResolvedValue(page([])),
    createCustomGroup: vi.fn().mockResolvedValue(group)
  });

  await screen.findByRole("row", { name: /first/ });
  await user.click(screen.getByRole("button", { name: "下一页" }));

  await waitFor(() => expect(listCards).toHaveBeenCalledWith("group-12", 2, 20));
  await waitFor(() => expect(listCards).toHaveBeenLastCalledWith("group-12", 1, 20));
  expect(await screen.findByRole("row", { name: /first/ })).toBeVisible();
  expect(screen.queryByRole("button", { name: "上一页" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "下一页" })).not.toBeInTheDocument();
});

test("groupId 变更时从第一页加载新分组，不沿用旧分页", async () => {
  const user = userEvent.setup();
  const listCards = vi.fn<GroupRepository["listCards"]>((groupId, requestedPage = 1) =>
    Promise.resolve(page([card(`${groupId}-${requestedPage}`, `${groupId}-word`, "考义")], requestedPage, 2))
  );
  const view = renderWithGroups("/groups/group-12", {
    listGroups: vi.fn().mockResolvedValue([group]),
    getDetail: vi.fn((groupId) => Promise.resolve({ ...group, groupId, name: groupId })),
    listCards,
    listUnassigned: vi.fn().mockResolvedValue(page([])),
    createCustomGroup: vi.fn().mockResolvedValue(group)
  });

  await screen.findByRole("row", { name: /group-12-word/ });
  await user.click(screen.getByRole("button", { name: "下一页" }));
  await waitFor(() => expect(listCards).toHaveBeenCalledWith("group-12", 2, 20));
  listCards.mockClear();

  await view.navigate("/groups/group-next");
  await screen.findByRole("heading", { name: "group-next" });
  expect(listCards).toHaveBeenCalledWith("group-next", 1, 20);
  expect(listCards).not.toHaveBeenCalledWith("group-next", 2, 20);
});

test("较早 groupId 的晚到响应不得覆盖新分组详情", async () => {
  const staleDetail = deferred<WordGroup>();
  const staleCards = deferred<GroupCardPage>();
  const nextGroup = { ...group, groupId: "group-next", name: "最新分组" };
  const groups: GroupRepository = {
    listGroups: vi.fn().mockResolvedValue([group]),
    getDetail: vi.fn((groupId) => groupId === "group-12" ? staleDetail.promise : Promise.resolve(nextGroup)),
    listCards: vi.fn((groupId) => groupId === "group-12" ? staleCards.promise : Promise.resolve(page([card("card-next", "fresh", "最新")]))),
    listUnassigned: vi.fn().mockResolvedValue(page([])),
    createCustomGroup: vi.fn().mockResolvedValue(group)
  };
  const app = renderWithGroups("/groups/group-12", groups);
  await app.navigate("/groups/group-next");
  expect(await screen.findByRole("heading", { name: "最新分组" })).toBeVisible();
  await act(async () => {
    staleDetail.resolve(group);
    staleCards.resolve(page([card("card-stale", "stale", "过期") ]));
  });
  expect(screen.queryByRole("heading", { name: "第 12 组 · 城市与环境" })).not.toBeInTheDocument();
  expect(screen.queryByText("stale")).not.toBeInTheDocument();
});

test("新建自定义分组先提示空选择，成功反馈可观察后返回词书", async () => {
  const user = userEvent.setup();
  const candidate = card("card-sustainable", "sustainable", "可持续的");
  const listUnassigned = vi.fn<GroupRepository["listUnassigned"]>().mockResolvedValue(page([candidate]));
  const createCustomGroup = vi.fn<GroupRepository["createCustomGroup"]>().mockResolvedValue({
    ...group,
    groupId: "custom-1",
    name: "环保词汇",
    source: "custom"
  });
  renderWithGroups("/groups/new", {
    listGroups: vi.fn().mockResolvedValue([group]),
    getDetail: vi.fn().mockResolvedValue(group),
    listCards: vi.fn().mockResolvedValue(page([])),
    listUnassigned,
    createCustomGroup
  });

  expect(await screen.findByRole("heading", { name: "新建自定义分组" })).toBeVisible();
  expect(listUnassigned).toHaveBeenCalledWith({ all: true, page: 1, size: 100 });
  await user.click(screen.getByRole("button", { name: "保存分组" }));
  expect(screen.getByText("请先选择单词")).toBeVisible();
  await user.click(screen.getByRole("checkbox", { name: /sustainable/ }));
  expect(screen.getByText("已选 1 个")).toBeVisible();
  await user.type(screen.getByRole("textbox", { name: "分组名称" }), "环保词汇");
  await user.click(screen.getByRole("button", { name: "保存分组" }));
  expect(await screen.findByText("已创建包含 1 张卡片的自定义分组")).toBeVisible();
  expect(createCustomGroup).toHaveBeenCalledWith({ name: "环保词汇", cardIds: ["card-sustainable"] });
  expect(await screen.findByRole("heading", { name: "词书与学习计划" }, { timeout: 2_000 })).toBeVisible();
});

test("409 后保留名称、刷新候选交集并公告冲突", async () => {
  const user = userEvent.setup();
  const stale = card("card-stale", "stale", "已被分组");
  const valid = card("card-valid", "valid", "仍可选");
  const conflict: AppError = { kind: "conflict", message: "server internal conflict detail" };
  const listUnassigned = vi.fn<GroupRepository["listUnassigned"]>()
    .mockResolvedValueOnce(page([stale, valid]))
    .mockResolvedValueOnce(page([valid]));
  const createCustomGroup = vi.fn<GroupRepository["createCustomGroup"]>()
    .mockRejectedValueOnce(conflict)
    .mockResolvedValueOnce({ ...group, groupId: "custom-1", source: "custom" });
  renderWithGroups("/groups/new", {
    listGroups: vi.fn().mockResolvedValue([group]),
    getDetail: vi.fn().mockResolvedValue(group),
    listCards: vi.fn().mockResolvedValue(page([])),
    listUnassigned,
    createCustomGroup
  });

  await user.type(await screen.findByRole("textbox", { name: "分组名称" }), "我的分组");
  await user.click(screen.getByRole("checkbox", { name: /stale/ }));
  await user.click(screen.getByRole("checkbox", { name: /valid/ }));
  await user.click(screen.getByRole("button", { name: "保存分组" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("部分选择已失效，请刷新候选后重新确认");
  expect(screen.getByRole("textbox", { name: "分组名称" })).toHaveValue("我的分组");
  expect(screen.queryByRole("checkbox", { name: /stale/ })).not.toBeInTheDocument();
  expect(screen.getByRole("checkbox", { name: /valid/ })).toBeChecked();
  expect(screen.getByText("已选 1 个")).toBeVisible();
  expect(screen.queryByText("server internal conflict detail")).not.toBeInTheDocument();

  await user.click(screen.getByRole("button", { name: "保存分组" }));
  expect(await screen.findByText("已创建包含 1 张卡片的自定义分组")).toBeVisible();
});

test("409 刷新失败仍立即公告冲突，重试成功后清理失效选择", async () => {
  const user = userEvent.setup();
  const stale = card("card-stale", "stale", "已被分组");
  const valid = card("card-valid", "valid", "仍可选");
  const conflict: AppError = { kind: "conflict", message: "server conflict detail" };
  const refreshError: AppError = { kind: "unavailable", message: "暂时无法刷新候选", retryable: true };
  const listUnassigned = vi.fn<GroupRepository["listUnassigned"]>()
    .mockResolvedValueOnce(page([stale, valid]))
    .mockRejectedValueOnce(refreshError)
    .mockResolvedValueOnce(page([valid]));
  const createCustomGroup = vi.fn<GroupRepository["createCustomGroup"]>()
    .mockRejectedValueOnce(conflict)
    .mockResolvedValueOnce({ ...group, groupId: "custom-2", source: "custom" });
  renderWithGroups("/groups/new", {
    listGroups: vi.fn().mockResolvedValue([group]),
    getDetail: vi.fn().mockResolvedValue(group),
    listCards: vi.fn().mockResolvedValue(page([])),
    listUnassigned,
    createCustomGroup
  });

  await user.type(await screen.findByRole("textbox", { name: "分组名称" }), "保留的名称");
  await user.click(screen.getByRole("checkbox", { name: /stale/ }));
  await user.click(screen.getByRole("checkbox", { name: /valid/ }));
  await user.click(screen.getByRole("button", { name: "保存分组" }));

  expect(await screen.findByText("部分选择已失效，请刷新候选后重新确认")).toBeVisible();
  expect(await screen.findByText("暂时无法刷新候选")).toBeVisible();
  expect(screen.getByRole("textbox", { name: "分组名称" })).toHaveValue("保留的名称");
  expect(screen.getByRole("checkbox", { name: /stale/ })).toBeChecked();

  await user.click(screen.getByRole("button", { name: "重新尝试" }));
  await waitFor(() => expect(screen.queryByRole("checkbox", { name: /stale/ })).not.toBeInTheDocument());
  expect(screen.getByRole("checkbox", { name: /valid/ })).toBeChecked();
  expect(screen.getByText("已选 1 个")).toBeVisible();
  expect(screen.getByRole("textbox", { name: "分组名称" })).toHaveValue("保留的名称");

  await user.click(screen.getByRole("button", { name: "保存分组" }));
  await waitFor(() => expect(createCustomGroup).toHaveBeenLastCalledWith({
    name: "保留的名称",
    cardIds: ["card-valid"]
  }));
});

test("任意异常不得把内部信息显示到分组页", async () => {
  renderWithGroups("/groups", {
    listGroups: vi.fn().mockRejectedValue(new Error("token=SECRET")),
    getDetail: vi.fn().mockResolvedValue(group),
    listCards: vi.fn().mockResolvedValue(page([])),
    listUnassigned: vi.fn().mockResolvedValue(page([])),
    createCustomGroup: vi.fn().mockResolvedValue(group)
  });

  expect(await screen.findByText("暂时无法获取分组")).toBeVisible();
  expect(screen.queryByText("token=SECRET")).not.toBeInTheDocument();
});

test("自定义分组最多选择 500 张卡片", async () => {
  const candidates = Array.from({ length: 501 }, (_, index) => card(`card-${index}`, `word-${index}`, `考义 ${index}`));
  renderWithGroups("/groups/new", {
    listGroups: vi.fn().mockResolvedValue([group]),
    getDetail: vi.fn().mockResolvedValue(group),
    listCards: vi.fn().mockResolvedValue(page([])),
    listUnassigned: vi.fn().mockResolvedValue({ ...page(candidates), size: 501, totalElements: 501 }),
    createCustomGroup: vi.fn().mockResolvedValue(group)
  });

  await screen.findByRole("checkbox", { name: /word-500/ });
  const checkboxes = screen.getAllByRole("checkbox");
  act(() => {
    checkboxes.slice(0, 500).forEach((checkbox) => checkbox.click());
  });
  expect(screen.getByText("已选 500 个")).toBeVisible();
  expect(checkboxes[500]).toBeDisabled();
}, 30_000);

test("从无效分组导航到有效分组后清除错误并恢复详情", async () => {
  const app = renderAuthenticatedApp("/groups/group-missing");

  expect(await screen.findByText("找不到指定分组")).toBeVisible();
  await app.navigate("/groups/group-12");

  expect(await screen.findByRole("heading", { name: "第 12 组 · 城市与环境" })).toBeVisible();
  expect(screen.getByRole("row", { name: /sustainable/ })).toBeVisible();
  expect(screen.queryByText("找不到指定分组")).not.toBeInTheDocument();
});

test("候选加载完成前卸载页面不应留下异步更新", async () => {
  const pending = deferred<GroupCardPage>();
  const groups: GroupRepository = {
    listGroups: vi.fn().mockResolvedValue([group]),
    getDetail: vi.fn().mockResolvedValue(group),
    listCards: vi.fn().mockResolvedValue(page([])),
    listUnassigned: vi.fn().mockReturnValue(pending.promise),
    createCustomGroup: vi.fn().mockResolvedValue(group)
  };
  const view = renderWithGroups("/groups/new", groups);

  await waitFor(() => expect(groups.listUnassigned).toHaveBeenCalledWith({ all: true, page: 1, size: 100 }));
  view.unmount();
  await act(async () => pending.resolve(page([card("late", "late", "晚到")])));
  expect(screen.queryByText("late")).not.toBeInTheDocument();
});
