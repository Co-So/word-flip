import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode } from "react";
import { MemoryRouter } from "react-router-dom";
import { AppProviders } from "@/app/AppProviders";
import { createMockRepositoryBundle } from "@/data/mock/fixtures";
import { createDemoState } from "@/data/mock/createDemoState";
import { DemoStateStore } from "@/data/mock/DemoStateStore";
import { MockBookRepository } from "@/data/mock/repositories/MockBookRepository";
import { RepositoryProvider } from "@/data/runtime/RepositoryContext";
import type { BookOverview, LearningPlan } from "@/domain/books";
import { BooksPage } from "@/features/books/BooksPage";
import { renderStateApp } from "@/test/renderApp";
import { afterEach, vi } from "vitest";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve;
    reject = nextReject;
  });
  return { promise, reject, resolve };
}

afterEach(() => {
  vi.restoreAllMocks();
});

test("Repository 读取失败时显示远端错误且不静默回放 Mock 词书", async () => {
  vi.spyOn(MockBookRepository.prototype, "list").mockRejectedValue({
    kind: "network",
    message: "HTTP 词书读取失败",
    fieldErrors: {}
  });
  renderStateApp(createDemoState("configured"), "/books");

  expect(await screen.findByRole("heading", { name: "暂时无法加载" })).toBeVisible();
  expect(screen.getByText("HTTP 词书读取失败")).toBeVisible();
  expect(screen.queryByRole("article", { name: "核心词汇" })).not.toBeInTheDocument();
});

test("激活 pending 时同步拒绝其他操作并在本次重载完成后统一恢复", async () => {
  const activation = deferred<LearningPlan>();
  const activateSpy = vi.spyOn(MockBookRepository.prototype, "activateBook")
    .mockReturnValue(activation.promise);
  const user = userEvent.setup();
  const app = renderStateApp(createDemoState("configured"), "/books");
  const advancedBook = await screen.findByRole("article", { name: "进阶词汇" });
  const ieltsBook = screen.getByRole("article", { name: "雅思核心词汇" });
  const advancedAction = within(advancedBook).getByRole("button", { name: "切换到此计划" });
  const ieltsAction = within(ieltsBook).getByRole("button", { name: "开始学习" });

  await user.click(advancedAction);
  await user.click(ieltsAction);
  await user.click(advancedAction);

  expect(activateSpy).toHaveBeenCalledTimes(1);
  expect(advancedAction).toBeDisabled();
  expect(ieltsAction).toBeDisabled();

  app.store.update((draft) => { draft.books.activePlanId = "plan-advanced"; });
  activation.resolve({ planId: "plan-advanced", bookId: "book-advanced", title: "进阶词汇" });

  expect(await within(advancedBook).findByRole("button", { name: "当前计划" })).toBeDisabled();
  expect(within(ieltsBook).getByRole("button", { name: "开始学习" })).toBeEnabled();
  expect(activateSpy).toHaveBeenCalledTimes(1);
});

test("激活失败后重试加载会恢复全部计划操作", async () => {
  const activation = deferred<LearningPlan>();
  const activateSpy = vi.spyOn(MockBookRepository.prototype, "activateBook")
    .mockReturnValue(activation.promise);
  const user = userEvent.setup();
  renderStateApp(createDemoState("configured"), "/books");
  const advancedBook = await screen.findByRole("article", { name: "进阶词汇" });
  const ieltsBook = screen.getByRole("article", { name: "雅思核心词汇" });

  await user.click(within(advancedBook).getByRole("button", { name: "切换到此计划" }));
  expect(within(ieltsBook).getByRole("button", { name: "开始学习" })).toBeDisabled();
  activation.reject({ kind: "network", message: "激活计划失败", fieldErrors: {} });

  expect(await screen.findByText("激活计划失败")).toBeVisible();
  await user.click(screen.getByRole("button", { name: "重新尝试" }));

  const recoveredAdvanced = await screen.findByRole("article", { name: "进阶词汇" });
  const recoveredIelts = screen.getByRole("article", { name: "雅思核心词汇" });
  expect(within(recoveredAdvanced).getByRole("button", { name: "切换到此计划" })).toBeEnabled();
  expect(within(recoveredIelts).getByRole("button", { name: "开始学习" })).toBeEnabled();
  expect(activateSpy).toHaveBeenCalledTimes(1);
});

test("StrictMode 中较旧的词书响应不得覆盖较新的加载结果", async () => {
  const staleLoad = deferred<BookOverview[]>();
  const freshBook: BookOverview = {
    bookId: "book-fresh",
    title: "最新词书",
    cardCount: 10,
    planId: null,
    planStatus: "available",
    progress: null
  };
  vi.spyOn(MockBookRepository.prototype, "list")
    .mockReturnValueOnce(staleLoad.promise)
    .mockResolvedValueOnce([freshBook]);
  const store = new DemoStateStore({ initialState: createDemoState("configured"), storage: null });
  const repositories = createMockRepositoryBundle(store);
  render(
    <StrictMode>
      <AppProviders>
        <RepositoryProvider repositories={repositories}>
          <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
            <BooksPage />
          </MemoryRouter>
        </RepositoryProvider>
      </AppProviders>
    </StrictMode>
  );

  expect(await screen.findByRole("article", { name: "最新词书" })).toBeVisible();
  await act(async () => {
    staleLoad.resolve([{
      ...freshBook,
      bookId: "book-stale",
      title: "过期词书"
    }]);
  });

  await waitFor(() => expect(screen.queryByRole("article", { name: "过期词书" })).not.toBeInTheDocument());
  expect(screen.getByRole("article", { name: "最新词书" })).toBeVisible();
});

test("无历史计划的词书回放固定快照并原子激活", async () => {
  const user = userEvent.setup();
  const initialState = createDemoState("configured");
  initialState.planStates["plan-core"].groups.items.push({
    groupId: "group-history-sentinel",
    name: "历史 sentinel",
    cardIds: []
  });
  const app = renderStateApp(initialState, "/books");

  const ieltsBook = await screen.findByRole("article", { name: "雅思核心词汇" });
  await user.click(within(ieltsBook).getByRole("button", { name: "开始学习" }));

  const state = app.store.read();
  expect(state.books.activePlanId).toBe("plan-ielts");
  expect(state.books.plans.filter((plan) => plan.planId === "plan-ielts")).toHaveLength(1);
  expect(state.planStates["plan-ielts"].today.tasks.newWords.count).toBe(20);
  expect(state.planStates["plan-core"].groups.items).toContainEqual({
    groupId: "group-history-sentinel",
    name: "历史 sentinel",
    cardIds: []
  });
});

test("切换计划只改变当前资源视图且切回后历史分组与进度仍在", async () => {
  const user = userEvent.setup();
  const initialState = createDemoState("configured");
  initialState.planStates["plan-core"].groups.items.push({
    groupId: "group-history-sentinel",
    name: "历史 sentinel",
    cardIds: ["card-sustainable"]
  });
  initialState.planStates["plan-core"].cards.byCardId["card-sustainable"].progress.choice.heatLevel = 3;
  const app = renderStateApp(initialState, "/books");

  const advancedBook = await screen.findByRole("article", { name: "进阶词汇" });
  await user.click(within(advancedBook).getByRole("button", { name: "切换到此计划" }));
  await app.navigate("/groups");
  expect(await screen.findByText("进阶复习")).toBeVisible();
  expect(screen.queryByText("历史 sentinel")).not.toBeInTheDocument();

  await app.navigate("/books");
  const coreBook = await screen.findByRole("article", { name: "核心词汇" });
  await user.click(within(coreBook).getByRole("button", { name: "切换到此计划" }));
  await app.navigate("/groups/group-history-sentinel");

  expect(await screen.findByRole("heading", { name: "历史 sentinel" })).toBeVisible();
  const sustainable = screen.getByRole("row", { name: /sustainable/ });
  expect(within(sustainable).getByText("热力 3")).toBeVisible();
});

test("词书详情只为当前计划展示预计算进度", async () => {
  const initialState = createDemoState("configured");
  initialState.planStates["plan-core"].bookProgress = {
    masteredCount: 126,
    assignedCardCount: 300,
    completionPercent: 42
  };
  renderStateApp(initialState, "/books/book-core");

  expect(await screen.findByRole("heading", { name: "核心词汇" })).toBeVisible();
  expect(screen.getByText("42%")).toBeVisible();
  expect(screen.getByText("126 / 300 张已掌握")).toBeVisible();
});

test("空书架使用真实数据源文案而不提演示数据", async () => {
  renderStateApp(createDemoState("empty-books"), "/books");

  expect(await screen.findByText("当前没有可创建计划的已发布词书。")).toBeVisible();
  expect(screen.queryByText(/演示数据/)).not.toBeInTheDocument();
});

test("无计划与已有计划缺失进度时在列表和详情显示不同真实状态", async () => {
  const app = renderStateApp(createDemoState("configured"), "/books");

  const availableBook = await screen.findByRole("article", { name: "雅思核心词汇" });
  const historyBook = screen.getByRole("article", { name: "进阶词汇" });
  expect(within(availableBook).getByText("创建学习计划后即可查看进度。")).toBeVisible();
  expect(within(historyBook).getByText("进度统计暂时不可用，请稍后重试。")).toBeVisible();
  expect(screen.queryByText(/固定计划快照|切回后可查看/)).not.toBeInTheDocument();

  await app.navigate("/books/book-advanced");
  expect(await screen.findByRole("heading", { name: "进阶词汇" })).toBeVisible();
  expect(screen.getByText("进度统计暂时不可用，请稍后重试。")).toBeVisible();

  await app.navigate("/books/book-ielts");
  expect(await screen.findByRole("heading", { name: "雅思核心词汇" })).toBeVisible();
  expect(screen.getByText("创建学习计划后即可查看进度。")).toBeVisible();
});
