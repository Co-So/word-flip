import { screen } from "@testing-library/react";
import { renderApp } from "@/test/renderApp";

test("根路由可以渲染应用启动状态", async () => {
  renderApp("/");

  expect(await screen.findByRole("status")).toHaveTextContent("正在加载 WordFlip");
});

test("测试渲染辅助不修改浏览器地址", () => {
  const isolatedPath = "/render-app-isolation";
  window.history.pushState({}, "", isolatedPath);

  renderApp("/");

  expect(window.location.pathname).toBe(isolatedPath);
});
