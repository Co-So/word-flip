/* eslint-disable react-refresh/only-export-components -- 测试导航桥与渲染辅助必须放在同一模块。 */
import { act, render, type RenderResult } from "@testing-library/react";
import { MemoryRouter, useNavigate } from "react-router-dom";
import { App } from "@/app/App";
import { AppProviders } from "@/app/AppProviders";
import { createMockRepositoryBundle } from "@/data/mock/fixtures";
import { DemoStateStore } from "@/data/mock/DemoStateStore";
import { createDemoState, type DemoScenario, type DemoState } from "@/data/mock/createDemoState";
import { RepositoryProvider } from "@/data/runtime/RepositoryContext";

export interface TestAppHandle extends RenderResult {
  store: DemoStateStore;
  navigate: (to: string) => Promise<void>;
}

function NavigationBridge({ onReady }: { onReady: (navigate: ReturnType<typeof useNavigate>) => void }) {
  onReady(useNavigate());
  return null;
}

function renderTestApp({
  route,
  initialState
}: {
  route: string;
  initialState: DemoState;
}): TestAppHandle {
  const store = new DemoStateStore({ initialState, storage: null });
  const repositories = createMockRepositoryBundle(store);
  let navigate: ReturnType<typeof useNavigate> | null = null;
  const result = render(
    <AppProviders>
      <RepositoryProvider repositories={repositories}>
        <MemoryRouter initialEntries={[route]} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
          <NavigationBridge onReady={(nextNavigate) => { navigate = nextNavigate; }} />
          <App />
        </MemoryRouter>
      </RepositoryProvider>
    </AppProviders>
  );

  return {
    ...result,
    store,
    navigate: async (to) => {
      await act(async () => {
        navigate?.(to);
      });
    }
  };
}

export function renderAuthenticatedApp(route: string): TestAppHandle {
  return renderTestApp({ route, initialState: createDemoState("configured") });
}

export function renderScenarioApp(scenario: DemoScenario, route: string): TestAppHandle {
  return renderTestApp({ route, initialState: createDemoState(scenario) });
}

export function renderApp(route: string): TestAppHandle {
  return renderAuthenticatedApp(route);
}

export function renderStateApp(initialState: DemoState, route: string): TestAppHandle {
  return renderTestApp({ route, initialState });
}
