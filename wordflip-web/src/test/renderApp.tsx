import { render, type RenderResult } from "@testing-library/react";
import { RouterProvider } from "react-router-dom";
import { AppProviders } from "@/app/AppProviders";
import { createAppRouter } from "@/app/router";

export function renderApp(route: string): RenderResult {
  window.history.pushState({}, "", route);

  return render(
    <AppProviders>
      <RouterProvider
        future={{ v7_startTransition: true }}
        router={createAppRouter()}
      />
    </AppProviders>
  );
}
