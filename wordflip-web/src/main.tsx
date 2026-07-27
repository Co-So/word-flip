import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { AppProviders } from "@/app/AppProviders";
import { createAppRouter } from "@/app/router";
import { createMockRepositoryBundle } from "@/data/mock/fixtures";
import { createDemoStateStore } from "@/data/mock/DemoStateStore";
import { bootstrapDemoScenario } from "@/data/mock/demoScenarioBootstrap";
import { RepositoryProvider } from "@/data/runtime/RepositoryContext";
import "@/design-system/global.css";

const rootElement = document.getElementById("root");

if (import.meta.env.DEV) {
  // E2E 每次从显式场景快照开始，不能继承上一次浏览器测试的演示状态。
  bootstrapDemoScenario({
    isDev: true,
    location: window.location,
    history: window.history,
    storage: window.localStorage
  });
}

const demoStore = createDemoStateStore();
const repositories = createMockRepositoryBundle(demoStore);

if (!rootElement) {
  throw new Error("找不到 WordFlip 应用根节点");
}

// 浏览器入口统一装配数据 Provider 与根路由。
createRoot(rootElement).render(
  <StrictMode>
    <AppProviders>
      <RepositoryProvider repositories={repositories}>
        <RouterProvider
          future={{ v7_startTransition: true }}
          router={createAppRouter()}
        />
      </RepositoryProvider>
    </AppProviders>
  </StrictMode>
);
