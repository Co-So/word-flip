import { render, screen } from "@testing-library/react";
import { RouterProvider } from "react-router-dom";
import { AppProviders } from "@/app/AppProviders";
import { createAppRouter } from "@/app/router";
import { createMockRepositoryBundle } from "@/data/mock/fixtures";
import { createDemoStateStore } from "@/data/mock/DemoStateStore";
import { RepositoryProvider } from "@/data/runtime/RepositoryContext";

test("生产路由可渲染当前计划的今日占位页", async () => {
  window.history.pushState({}, "", "/today");
  const repositories = createMockRepositoryBundle(createDemoStateStore({ storage: null }));

  render(
    <AppProviders>
      <RepositoryProvider repositories={repositories}>
        <RouterProvider
          future={{ v7_startTransition: true }}
          router={createAppRouter()}
        />
      </RepositoryProvider>
    </AppProviders>
  );

  expect(await screen.findByRole("heading", { name: "今天继续前进" })).toBeVisible();
});
