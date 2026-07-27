import { screen } from "@testing-library/react";
import { renderApp } from "@/test/renderApp";

test("根路由会进入当前学习计划的今日占位页", async () => {
  renderApp("/");

  expect(await screen.findByRole("heading", { name: "今天继续前进" })).toBeVisible();
});

test("测试渲染辅助不修改浏览器地址", async () => {
  const isolatedPath = "/render-app-isolation";
  window.history.pushState({}, "", isolatedPath);

  renderApp("/");

  await screen.findByRole("heading", { name: "今天继续前进" });

  expect(window.location.pathname).toBe(isolatedPath);
});
