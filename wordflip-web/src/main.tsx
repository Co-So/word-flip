import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { AppProviders } from "@/app/AppProviders";
import { createAppRouter } from "@/app/router";
import { createMockRepositoryBundle } from "@/data/mock/fixtures";
import { createDemoStateStore } from "@/data/mock/DemoStateStore";
import { RepositoryProvider } from "@/data/runtime/RepositoryContext";
import "@/design-system/global.css";

const rootElement = document.getElementById("root");
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
