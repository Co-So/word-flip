import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { RequireOnboarding } from "@/app/routeGuards";
import { createDemoStateStore } from "@/data/mock/DemoStateStore";
import { createMockRepositoryBundle } from "@/data/mock/fixtures";
import { RepositoryProvider } from "@/data/runtime/RepositoryContext";

test("当前计划检查失败时可重试，并在恢复后渲染受保护内容", async () => {
  const user = userEvent.setup();
  const repositories = createMockRepositoryBundle(createDemoStateStore({ storage: null }));
  vi.spyOn(repositories.books, "getActivePlan")
    .mockRejectedValueOnce(new Error("network unavailable"))
    .mockResolvedValue({ planId: "plan-9", bookId: "book-3", title: "考研英语核心词" });

  render(
    <RepositoryProvider repositories={repositories}>
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
        <RequireOnboarding>
          <h1>受保护内容</h1>
        </RequireOnboarding>
      </MemoryRouter>
    </RepositoryProvider>
  );

  expect(await screen.findByRole("alert")).toHaveTextContent("无法确认当前学习计划");
  await user.click(screen.getByRole("button", { name: "重试" }));
  expect(await screen.findByRole("heading", { name: "受保护内容" })).toBeVisible();
});
