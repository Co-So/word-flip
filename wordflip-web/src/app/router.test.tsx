import { render, screen } from "@testing-library/react";
import { RouterProvider } from "react-router-dom";
import { AppProviders } from "@/app/AppProviders";
import { createAppRouter } from "@/app/router";

test("生产根路由可以渲染应用启动状态", async () => {
  window.history.pushState({}, "", "/");

  render(
    <AppProviders>
      <RouterProvider
        future={{ v7_startTransition: true }}
        router={createAppRouter()}
      />
    </AppProviders>
  );

  expect(await screen.findByRole("status")).toHaveTextContent("正在加载 WordFlip");
});
