import { screen, within } from "@testing-library/react";
import { renderAuthenticatedApp, renderScenarioApp } from "@/test/renderApp";

test("今日页只展示当前计划的四项固定摘要和最多三条最近学习", async () => {
  renderAuthenticatedApp("/today");

  const summary = await screen.findByRole("list", { name: "今日摘要" });
  expect(within(summary).getByText("24")).toBeVisible();
  expect(within(summary).getByText("126")).toBeVisible();
  expect(within(summary).getByText("18")).toBeVisible();
  expect(within(summary).getByText("72%")).toBeVisible();
  expect(screen.getByText("推荐学习 · 第 12 组 · 城市与环境")).toBeVisible();
  expect(screen.getAllByRole("listitem", { name: /最近学习/ })).toHaveLength(3);
  expect(screen.getByText("TODAY · READY")).toBeVisible();
  expect(screen.getByRole("link", { name: "开始今日学习" })).toHaveAttribute(
    "href",
    "/study/study-demo"
  );
});

test("无今日任务时显示完成反馈并可浏览词书", async () => {
  renderScenarioApp("empty-today", "/today");

  expect(await screen.findByRole("heading", { name: "今天的任务已完成" })).toBeVisible();
  expect(screen.getByRole("link", { name: "浏览词书" })).toHaveAttribute("href", "/books");
});
