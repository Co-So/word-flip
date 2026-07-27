import { createBrowserRouter } from "react-router-dom";
import { App } from "@/app/App";

export type AppRouter = ReturnType<typeof createBrowserRouter>;

export function createAppRouter(): AppRouter {
  return createBrowserRouter(
    [
      {
        path: "*",
        element: <App />
      }
    ],
    {
      future: {
        v7_relativeSplatPath: true
      }
    }
  );
}
