import { screen } from "@testing-library/react";
import { renderApp } from "@/test/renderApp";

test("根路由可以渲染应用启动状态", async () => {
  renderApp("/");
  expect(await screen.findByRole("status")).toHaveTextContent("正在加载 WordFlip");
});
