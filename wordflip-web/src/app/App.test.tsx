import { screen } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import * as appRouter from "@/app/router";
import { renderApp } from "@/test/renderApp";

afterEach(() => {
  vi.restoreAllMocks();
});

test("根路由可以渲染应用启动状态", async () => {
  const createAppRouterSpy = vi.spyOn(appRouter, "createAppRouter");

  renderApp("/");

  expect(createAppRouterSpy).toHaveBeenCalledOnce();
  expect(await screen.findByRole("status")).toHaveTextContent("正在加载 WordFlip");
});
