import { act, fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createDemoState } from "@/data/mock/createDemoState";
import { MockSettingsRepository } from "@/data/mock/repositories/MockSettingsRepository";
import type { LearningPlan } from "@/domain/books";
import { renderStateApp } from "@/test/renderApp";
import { afterEach, vi } from "vitest";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve;
    reject = nextReject;
  });
  return { promise, reject, resolve };
}

afterEach(() => {
  vi.restoreAllMocks();
});

function renderOnboardingApp() {
  const initialState = createDemoState("configured");
  initialState.books.activePlanId = null;
  initialState.books.plans = [];
  initialState.planStates = {};
  return renderStateApp(initialState, "/onboarding");
}

test("首次设置选择主词书和每组 30 词后进入今日", async () => {
  const user = userEvent.setup();
  const { store } = renderOnboardingApp();

  await user.click(await screen.findByRole("radio", { name: "雅思核心词汇" }));
  const groupSize = screen.getByLabelText("每组单词数");
  expect(groupSize).toHaveValue("20");
  await user.selectOptions(groupSize, "30");
  await user.click(screen.getByRole("button", { name: "完成设置" }));

  expect(await screen.findByRole("heading", { name: "今天继续前进" })).toBeVisible();
  expect(store.read().books.activePlanId).not.toBeNull();
});

test.each([
  ["雅思核心词汇", "book-ielts"],
  ["核心词汇", "book-core"],
  ["进阶词汇", "book-advanced"]
])("三本内置词书中的 %s 都能保存为当前学习计划", async (bookTitle, bookId) => {
  const user = userEvent.setup();
  const { store } = renderOnboardingApp();

  await user.click(await screen.findByRole("radio", { name: bookTitle }));
  await user.click(screen.getByRole("button", { name: "完成设置" }));

  expect(await screen.findByRole("heading", { name: "今天继续前进" })).toBeVisible();
  expect(store.read().books.activePlanId).not.toBeNull();
  expect(store.read().books.plans.find((plan) => plan.planId === store.read().books.activePlanId)?.bookId).toBe(bookId);
});

test("首次设置只允许需求定义的四种分组大小", async () => {
  renderOnboardingApp();

  expect(await screen.findByLabelText("每组单词数")).toHaveTextContent("10 词每组20 词每组30 词每组50 词每组");
  expect(screen.queryByRole("option", { name: /25/ })).not.toBeInTheDocument();
});

test("Repository 保存失败时停留首次设置且不静默创建 Mock 计划", async () => {
  vi.spyOn(MockSettingsRepository.prototype, "saveOnboarding").mockRejectedValue({
    kind: "network",
    message: "HTTP 设置保存失败",
    fieldErrors: {}
  });
  const user = userEvent.setup();
  const { store } = renderOnboardingApp();

  await user.click(await screen.findByRole("radio", { name: "核心词汇" }));
  await user.click(screen.getByRole("button", { name: "完成设置" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("HTTP 设置保存失败");
  expect(screen.getByRole("heading", { name: "设置你的学习计划" })).toBeVisible();
  expect(screen.queryByRole("heading", { name: "今天继续前进" })).not.toBeInTheDocument();
  expect(store.read().books.activePlanId).toBeNull();
});

test("首次设置 pending 时同步拒绝重复提交且卸载后的成功不得旧导航", async () => {
  const pendingSave = deferred<LearningPlan>();
  const saveSpy = vi.spyOn(MockSettingsRepository.prototype, "saveOnboarding")
    .mockReturnValue(pendingSave.promise);
  const user = userEvent.setup();
  const app = renderStateApp(createDemoState("configured"), "/onboarding");

  await user.click(await screen.findByRole("radio", { name: "核心词汇" }));
  const submit = screen.getByRole("button", { name: "完成设置" });
  const form = submit.closest("form");
  expect(form).not.toBeNull();
  fireEvent.submit(form!);
  fireEvent.submit(form!);
  expect(saveSpy).toHaveBeenCalledTimes(1);

  await app.navigate("/settings");
  expect(await screen.findByRole("heading", { name: "设置" })).toBeVisible();
  await act(async () => {
    pendingSave.resolve({ planId: "plan-core", bookId: "book-core", title: "核心词汇" });
    await pendingSave.promise;
  });

  expect(screen.getByRole("heading", { name: "设置" })).toBeVisible();
  expect(screen.queryByRole("heading", { name: "今天继续前进" })).not.toBeInTheDocument();
});

test("首次设置卸载后的失败不得向新页面写入旧错误", async () => {
  const pendingSave = deferred<LearningPlan>();
  vi.spyOn(MockSettingsRepository.prototype, "saveOnboarding").mockReturnValue(pendingSave.promise);
  const user = userEvent.setup();
  const app = renderStateApp(createDemoState("configured"), "/onboarding");

  await user.click(await screen.findByRole("radio", { name: "核心词汇" }));
  await user.click(screen.getByRole("button", { name: "完成设置" }));
  await app.navigate("/settings");
  expect(await screen.findByRole("heading", { name: "设置" })).toBeVisible();
  await act(async () => {
    pendingSave.reject({ kind: "network", message: "过期的首次设置错误", fieldErrors: {} });
    await pendingSave.promise.catch(() => undefined);
  });

  expect(screen.getByRole("heading", { name: "设置" })).toBeVisible();
  expect(screen.queryByText("过期的首次设置错误")).not.toBeInTheDocument();
});
