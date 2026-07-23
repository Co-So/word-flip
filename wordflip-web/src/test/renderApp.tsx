import { act, render, type RenderResult } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { App } from "@/app/App";
import { AppProviders } from "@/app/AppProviders";
import { createMockRepositoryBundle } from "@/data/mock/fixtures";
import { DemoStateStore } from "@/data/mock/DemoStateStore";
import { createDemoState, type DemoScenario } from "@/data/mock/createDemoState";
import { RepositoryProvider } from "@/data/runtime/RepositoryContext";

export interface TestAppHandle extends RenderResult {
  store: DemoStateStore;
  router: ReturnType<typeof createMemoryRouter>;
  navigate: (to: string) => Promise<void>;
}

function renderTestApp({
  route,
  scenario,
  authenticated
}: {
  route: string;
  scenario: DemoScenario;
  authenticated: boolean;
}): TestAppHandle {
  const state = createDemoState(authenticated ? scenario : "logged-out");
  const store = new DemoStateStore({ initialState: state, storage: null });
  const repositories = createMockRepositoryBundle(store);
  const router = createMemoryRouter([{ path: "*", element: <App /> }], {
    initialEntries: [route],
    future: { v7_relativeSplatPath: true }
  });
  const result = render(
    <AppProviders>
      <RepositoryProvider repositories={repositories}>
        <RouterProvider router={router} future={{ v7_startTransition: true }} />
      </RepositoryProvider>
    </AppProviders>
  );

  return {
    ...result,
    store,
    router,
    navigate: async (to) => {
      await act(async () => {
        await router.navigate(to);
      });
    }
  };
}

export function renderAuthenticatedApp(route: string): TestAppHandle {
  return renderTestApp({ route, scenario: "configured", authenticated: true });
}

export function renderScenarioApp(scenario: DemoScenario, route: string): TestAppHandle {
  return renderTestApp({ route, scenario, authenticated: scenario !== "logged-out" });
}

export function renderApp(route: string): TestAppHandle {
  return renderAuthenticatedApp(route);
}
