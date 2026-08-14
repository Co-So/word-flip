import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { App } from "@/app/App";
import { AppProviders } from "@/app/AppProviders";
import { createMockRepositoryBundle } from "@/data/mock/fixtures";
import { DemoStateStore } from "@/data/mock/DemoStateStore";
import { createDemoState } from "@/data/mock/createDemoState";
import { RepositoryProvider } from "@/data/runtime/RepositoryContext";
import { renderScenarioApp, renderStateApp } from "@/test/renderApp";

test("今日页展示服务端三项统计、中文日期与连续打卡", async () => {
  renderStateApp(createDemoState(), "/today");

  const formattedDate = new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    weekday: "long"
  }).format(new Date(2026, 6, 23));
  expect(await screen.findByText(formattedDate)).toBeVisible();
  expect(screen.getByText("连续打卡 14 天")).toBeVisible();

  const summary = screen.getByRole("list", { name: "今日摘要" });
  expect(within(summary).getByText("已掌握")).toBeVisible();
  expect(within(summary).getByText("126")).toBeVisible();
  expect(within(summary).getByText("待复习")).toBeVisible();
  expect(within(summary).getByText("24")).toBeVisible();
  expect(within(summary).getByText("计划完成度")).toBeVisible();
  expect(within(summary).getByText("72%")).toBeVisible();
  expect(within(summary).queryByText("测验任务")).not.toBeInTheDocument();
});

test("服务端返回无效日期或最近学习时间时展示稳定的中性回退", async () => {
  const state = createDemoState();
  state.planStates["plan-core"].today.date = "2026-02-30";
  state.planStates["plan-core"].today.recentGroups[0].lastStudiedAt = "not-a-timestamp";

  renderStateApp(state, "/today");

  expect(await screen.findByText("日期待确认")).toBeVisible();
  expect(screen.getByText("时间待确认")).toBeVisible();
  expect(screen.queryByText(/NaN/)).not.toBeInTheDocument();
});

test("最近分组位于任务前方且最多展示三条真实分组入口", async () => {
  const state = createDemoState();
  state.planStates["plan-core"].today.recentGroups.push({
    groupId: "group-9",
    name: "第 9 组",
    lastStudiedAt: "2026-07-21T08:00:00Z"
  });

  renderStateApp(state, "/today");

  const recent = await screen.findByRole("heading", { name: "最近学习" });
  const tasks = screen.getByRole("heading", { name: "今日任务" });
  expect(recent.compareDocumentPosition(tasks) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  expect(screen.getAllByRole("link", { name: /最近学习/ })).toHaveLength(3);
  expect(screen.getByRole("link", { name: "最近学习 第 12 组 · 城市与环境" })).toHaveAttribute(
    "href",
    "/groups/group-12"
  );
});

test("新词和到期任务优先使用首个来源分组，没有来源时回退推荐分组", async () => {
  const state = createDemoState();
  const today = state.planStates["plan-core"].today;
  today.tasks.newWords.sources = [];
  today.tasks.dueReview.sources = [{ groupId: "group-due", groupName: "待复习组", count: 24 }];

  renderStateApp(state, "/today");

  expect(await screen.findByRole("link", { name: /^新词/ })).toHaveAttribute("href", "/groups/group-12");
  expect(screen.getByRole("link", { name: /^到期复习/ })).toHaveAttribute("href", "/groups/group-due");
});

test("测验任务保持非链接提示，且不再出现模拟学习会话入口", async () => {
  renderStateApp(createDemoState(), "/today");

  expect(await screen.findByText("测验将在下一阶段开放")).toBeVisible();
  expect(screen.queryByRole("link", { name: "测验" })).not.toBeInTheDocument();
  expect(document.querySelector('[href*="study-demo"]')).not.toBeInTheDocument();
});

test("无今日任务时显示完成反馈并可浏览词书", async () => {
  renderScenarioApp("empty-today", "/today");

  expect(await screen.findByRole("heading", { name: "今天的任务已完成" })).toBeVisible();
  expect(screen.getByRole("link", { name: "浏览词书" })).toHaveAttribute("href", "/books");
});

test("无推荐分组但仍有任务时不宣称今日已完成", async () => {
  const state = createDemoState();
  state.planStates["plan-core"].today.recommendedStudy = null;

  renderStateApp(state, "/today");

  expect(await screen.findByText("今日任务已准备好")).toBeVisible();
  expect(screen.queryByText("今日任务已完成")).not.toBeInTheDocument();
  expect(screen.getByText("到期复习")).toBeVisible();
});

test("今日加载失败后重试成功会清除旧错误并展示最新快照", async () => {
  const user = userEvent.setup();
  const state = createDemoState();
  const repositories = createMockRepositoryBundle(new DemoStateStore({ initialState: state, storage: null }));
  vi.spyOn(repositories.today, "getSummary")
    .mockRejectedValueOnce(new Error("network unavailable"))
    .mockResolvedValueOnce(state.planStates["plan-core"].today);

  render(
    <AppProviders>
      <RepositoryProvider repositories={repositories}>
        <MemoryRouter initialEntries={["/today"]} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
          <App />
        </MemoryRouter>
      </RepositoryProvider>
    </AppProviders>
  );

  expect(await screen.findByRole("heading", { name: "暂时无法加载" })).toBeVisible();
  await user.click(screen.getByRole("button", { name: "重新尝试" }));
  const formattedDate = new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    weekday: "long"
  }).format(new Date(2026, 6, 23));
  expect(await screen.findByText(formattedDate)).toBeVisible();
  expect(screen.queryByRole("heading", { name: "暂时无法加载" })).not.toBeInTheDocument();
});
