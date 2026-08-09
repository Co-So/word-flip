import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { QueryClient } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";
import { StrictMode } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, test } from "vitest";
import { vi } from "vitest";
import { AppProviders } from "@/app/AppProviders";
import type { RepositoryBundle } from "@/data/contracts/RepositoryBundle";
import { createMockRepositoryBundle } from "@/data/mock/fixtures";
import { createDemoState } from "@/data/mock/createDemoState";
import { DemoStateStore } from "@/data/mock/DemoStateStore";
import { MockAuthRepository } from "@/data/mock/repositories/MockAuthRepository";
import { MockSettingsRepository } from "@/data/mock/repositories/MockSettingsRepository";
import { RepositoryProvider } from "@/data/runtime/RepositoryContext";
import type { AppSettings } from "@/domain/settings";
import { SettingsPage } from "@/features/settings/SettingsPage";
import { renderScenarioApp } from "@/test/renderApp";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve;
    reject = nextReject;
  });
  return { promise, reject, resolve };
}

function repositoriesFor(scenario: "configured" | "mutated" = "configured") {
  const store = new DemoStateStore({ initialState: createDemoState(scenario), storage: null });
  return createMockRepositoryBundle(store);
}

function SettingsTestApp({ repositories }: { repositories: RepositoryBundle }) {
  return <AppProviders>
    <RepositoryProvider repositories={repositories}>
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
        <SettingsPage />
      </MemoryRouter>
    </RepositoryProvider>
  </AppProviders>;
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("设置", () => {
  test("StrictMode 中较旧的设置加载不得覆盖较新的响应", async () => {
    const staleLoad = deferred<AppSettings>();
    const freshSettings: AppSettings = {
      soundEnabled: false,
      reducedMotion: true,
      groupSize: 30,
      groupStrategy: "frequency"
    };
    vi.spyOn(MockSettingsRepository.prototype, "getSettings")
      .mockReturnValueOnce(staleLoad.promise)
      .mockResolvedValueOnce(freshSettings);
    const repositories = repositoriesFor();
    render(<StrictMode><SettingsTestApp repositories={repositories} /></StrictMode>);

    const sound = await screen.findByRole("checkbox", { name: "播放发音" });
    expect(sound).not.toBeChecked();
    expect(screen.getByLabelText("每组单词数")).toHaveValue("30");
    await act(async () => {
      staleLoad.resolve({
        soundEnabled: true,
        reducedMotion: false,
        groupSize: 20,
        groupStrategy: "book_order"
      });
      await staleLoad.promise;
    });

    expect(sound).not.toBeChecked();
    expect(screen.getByLabelText("每组单词数")).toHaveValue("30");
  });

  test("较新的仓储加载会淘汰旧保存结果且不显示旧成功态", async () => {
    const staleSave = deferred<AppSettings>();
    const firstRepositories = repositoriesFor();
    const secondRepositories = repositoriesFor();
    vi.spyOn(firstRepositories.settings, "updateSettings").mockReturnValue(staleSave.promise);
    vi.spyOn(secondRepositories.settings, "getSettings").mockResolvedValue({
      soundEnabled: false,
      reducedMotion: true,
      groupSize: 30,
      groupStrategy: "frequency"
    });
    const user = userEvent.setup();
    const view = render(<SettingsTestApp repositories={firstRepositories} />);

    await user.click(await screen.findByRole("checkbox", { name: "播放发音" }));
    await user.click(screen.getByRole("button", { name: "保存设置" }));
    view.rerender(<SettingsTestApp repositories={secondRepositories} />);
    await waitFor(() => expect(screen.getByLabelText("每组单词数")).toHaveValue("30"));

    await act(async () => {
      staleSave.resolve({
        soundEnabled: true,
        reducedMotion: false,
        groupSize: 20,
        groupStrategy: "book_order"
      });
      await staleSave.promise;
    });

    expect(screen.getByRole("checkbox", { name: "播放发音" })).not.toBeChecked();
    expect(screen.getByLabelText("每组单词数")).toHaveValue("30");
    expect(screen.queryByRole("status", { name: "设置已保存" })).not.toBeInTheDocument();
  });

  test("重置 pending 时卸载页面，旧完成不得清缓存或导航", async () => {
    const pendingReset = deferred<AppSettings>();
    vi.spyOn(MockSettingsRepository.prototype, "resetDemo").mockReturnValue(pendingReset.promise);
    const clearSpy = vi.spyOn(QueryClient.prototype, "clear");
    const user = userEvent.setup();
    const app = renderScenarioApp("configured", "/settings");

    await user.click(await screen.findByRole("button", { name: "重置演示数据" }));
    await user.click(screen.getByRole("button", { name: "确认重置" }));
    await app.navigate("/books");
    expect(await screen.findByRole("heading", { name: "词书与学习计划" })).toBeVisible();
    await act(async () => {
      pendingReset.resolve(app.store.read().settings);
      await pendingReset.promise;
    });

    expect(clearSpy).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { name: "词书与学习计划" })).toBeVisible();
  });

  test("退出 pending 时卸载页面，旧完成不得清缓存或导航", async () => {
    const pendingSignOut = deferred<{ signedOut: true }>();
    vi.spyOn(MockAuthRepository.prototype, "signOut").mockReturnValue(pendingSignOut.promise);
    const clearSpy = vi.spyOn(QueryClient.prototype, "clear");
    const user = userEvent.setup();
    const app = renderScenarioApp("configured", "/settings");

    await user.click(await screen.findByRole("button", { name: "退出登录" }));
    await user.click(screen.getByRole("button", { name: "确认退出" }));
    await app.navigate("/books");
    expect(await screen.findByRole("heading", { name: "词书与学习计划" })).toBeVisible();
    await act(async () => {
      pendingSignOut.resolve({ signedOut: true });
      await pendingSignOut.promise;
    });

    expect(clearSpy).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { name: "词书与学习计划" })).toBeVisible();
  });

  test("不支持演示重置时完全隐藏演示面板且无法触发确认框", async () => {
    vi.spyOn(MockSettingsRepository.prototype, "supportsDemoReset").mockReturnValue(false);
    const resetSpy = vi.spyOn(MockSettingsRepository.prototype, "resetDemo");
    renderScenarioApp("configured", "/settings");

    expect(await screen.findByRole("heading", { name: "学习偏好" })).toBeVisible();
    expect(screen.queryByRole("heading", { name: "演示数据" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "重置演示数据" })).not.toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "确认重置演示数据" })).not.toBeInTheDocument();
    expect(screen.queryByText(/LOCAL DEMO/)).not.toBeInTheDocument();
    expect(screen.queryByText(/演示偏好/)).not.toBeInTheDocument();
    expect(resetSpy).not.toHaveBeenCalled();
  });

  test("保存失败显示可访问错误、恢复表单并在重试前清除旧状态", async () => {
    let rejectUpdate!: (reason: unknown) => void;
    const updatePromise = new Promise<Awaited<ReturnType<MockSettingsRepository["updateSettings"]>>>(
      (_resolve, reject) => { rejectUpdate = reject; }
    );
    const updateSpy = vi.spyOn(MockSettingsRepository.prototype, "updateSettings")
      .mockReturnValueOnce(updatePromise);
    const user = userEvent.setup();
    const app = renderScenarioApp("configured", "/settings");
    const sound = await screen.findByRole("checkbox", { name: "播放发音" });
    const save = screen.getByRole("button", { name: "保存设置" });

    await user.click(sound);
    await user.click(save);
    expect(sound).toBeDisabled();
    expect(save).toBeDisabled();

    rejectUpdate(new Error("后端内部细节不应展示"));
    expect(await screen.findByRole("alert")).toHaveTextContent("暂时无法保存设置");
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(sound).toBeEnabled();
    expect(save).toBeEnabled();

    updateSpy.mockResolvedValueOnce({ ...app.store.read().settings, soundEnabled: false });
    await user.click(save);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(await screen.findByRole("status")).toHaveTextContent("设置已保存");
  });

  test("退出登录需要确认，取消恢复焦点，确认后清理会话并返回登录页", async () => {
    const user = userEvent.setup();
    const app = renderScenarioApp("configured", "/settings");
    const trigger = await screen.findByRole("button", { name: "退出登录" });

    await user.click(trigger);
    const dialog = screen.getByRole("dialog", { name: "确认退出登录" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(screen.getByRole("button", { name: "取消" })).toHaveFocus();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog", { name: "确认退出登录" })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();

    await user.click(trigger);
    await user.click(screen.getByRole("button", { name: "确认退出" }));

    expect(await screen.findByRole("heading", { name: "登录 WordFlip" })).toBeVisible();
    expect(app.store.read().auth.session).toBeNull();
  });

  test("退出 pending 时只提交一次，意外失败后保留确认框并允许重试", async () => {
    let rejectSignOut!: (reason: unknown) => void;
    const signOutPromise = new Promise<{ signedOut: true }>((_resolve, reject) => {
      rejectSignOut = reject;
    });
    const signOutSpy = vi.spyOn(MockAuthRepository.prototype, "signOut").mockReturnValue(signOutPromise);
    const user = userEvent.setup();
    renderScenarioApp("configured", "/settings");

    await user.click(await screen.findByRole("button", { name: "退出登录" }));
    const confirm = screen.getByRole("button", { name: "确认退出" });
    await user.dblClick(confirm);
    await user.keyboard("{Enter}");

    expect(signOutSpy).toHaveBeenCalledTimes(1);
    expect(confirm).toBeDisabled();
    expect(screen.getByRole("button", { name: "取消" })).toBeDisabled();
    await user.keyboard("{Escape}");
    expect(screen.getByRole("dialog", { name: "确认退出登录" })).toBeVisible();

    rejectSignOut(new Error("暂时无法退出登录"));
    expect(await screen.findByRole("alert")).toHaveTextContent("暂时无法退出登录");
    expect(confirm).toBeEnabled();
    expect(screen.getByRole("button", { name: "取消" })).toBeEnabled();
  });

  test("保存固定设置快照并持久化，不出现全局词典切换", async () => {
    const user = userEvent.setup();
    const app = renderScenarioApp("configured", "/settings");

    await user.click(await screen.findByRole("checkbox", { name: "播放发音" }));
    await user.click(screen.getByRole("checkbox", { name: "减少动态效果" }));
    await user.selectOptions(screen.getByLabelText("每组单词数"), "30");
    await user.click(screen.getByRole("button", { name: "保存设置" }));

    expect(await screen.findByRole("status")).toHaveTextContent("设置已保存");
    expect(app.store.read().settings).toEqual({
      soundEnabled: false,
      reducedMotion: true,
      groupSize: 30
    });
    expect(screen.queryByText(/activeDictId|全局词典|切换词典/i)).not.toBeInTheDocument();
  });

  test("重置对话框困住焦点，Esc 关闭并恢复触发按钮焦点", async () => {
    const user = userEvent.setup();
    renderScenarioApp("configured", "/settings");
    const trigger = await screen.findByRole("button", { name: "重置演示数据" });

    await user.click(trigger);
    const dialog = screen.getByRole("dialog", { name: "确认重置演示数据" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(screen.getByRole("button", { name: "取消" })).toHaveFocus();

    await user.tab({ shift: true });
    expect(screen.getByRole("button", { name: "确认重置" })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole("button", { name: "取消" })).toHaveFocus();
    await user.keyboard("{Escape}");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  test("明确确认后恢复 126、清除当前变更并导航今日", async () => {
    const user = userEvent.setup();
    const app = renderScenarioApp("mutated", "/settings");
    app.store.update((draft) => {
      draft.planStates["plan-core"].today.masteredCount = 999;
      draft.settings.groupSize = 50;
    });

    await user.click(await screen.findByRole("button", { name: "重置演示数据" }));
    await user.click(screen.getByRole("button", { name: "确认重置" }));

    expect(await screen.findByRole("heading", { name: "今天继续前进" })).toBeVisible();
    await waitFor(() => expect(app.store.read().planStates["plan-core"].today.masteredCount).toBe(126));
    expect(app.store.read().settings.groupSize).toBe(20);
    expect(app.store.read().planStates["plan-core"].media.byCardId["card-sustainable"].stainLevel).toBe(0);
  });

  test("重置 pending 时重复确认只提交一次，Esc 不关闭，完成后才导航", async () => {
    let resolveReset!: (settings: Awaited<ReturnType<MockSettingsRepository["resetDemo"]>>) => void;
    const resetPromise = new Promise<Awaited<ReturnType<MockSettingsRepository["resetDemo"]>>>(
      (resolve) => { resolveReset = resolve; }
    );
    const resetSpy = vi.spyOn(MockSettingsRepository.prototype, "resetDemo").mockReturnValue(resetPromise);
    const user = userEvent.setup();
    const app = renderScenarioApp("configured", "/settings");
    await user.click(await screen.findByRole("button", { name: "重置演示数据" }));
    const confirm = screen.getByRole("button", { name: "确认重置" });

    await user.dblClick(confirm);
    await user.keyboard("{Enter}");
    expect(resetSpy).toHaveBeenCalledTimes(1);
    expect(confirm).toBeDisabled();
    expect(screen.getByRole("button", { name: "取消" })).toBeDisabled();
    await user.keyboard("{Escape}");
    expect(screen.getByRole("dialog", { name: "确认重置演示数据" })).toBeVisible();
    expect(screen.queryByRole("heading", { name: "今天继续前进" })).not.toBeInTheDocument();

    resolveReset(app.store.read().settings);

    expect(await screen.findByRole("heading", { name: "今天继续前进" })).toBeVisible();
  });
});
