import { render, type RenderResult } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { App } from "@/app/App";
import { AppProviders } from "@/app/AppProviders";

export function renderApp(route: string): RenderResult {
  return render(
    <AppProviders>
      <MemoryRouter
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
        initialEntries={[route]}
      >
        <App />
      </MemoryRouter>
    </AppProviders>
  );
}
