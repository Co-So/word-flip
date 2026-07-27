import { cleanup, screen } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { createDemoState } from "@/data/mock/createDemoState";
import { createDemoStateStore, DEMO_STORAGE_KEY } from "@/data/mock/DemoStateStore";
import { renderScenarioApp, renderStateApp } from "@/test/renderApp";

afterEach(() => cleanup());

describe("统计", () => {
  test("四项摘要、热力图、成就和双轨进度完全来自仓储快照", async () => {
    const state = createDemoState();
    const snapshot = state.planStates["plan-core"].stats;
    snapshot.totalReviewed = 4321;
    snapshot.masteredCount = 777;
    snapshot.retentionRate = 0.876;
    snapshot.streakDays = 23;
    snapshot.heatmapDays[0] = { date: "2025-08-01", intensity: 3, count: 9 };
    snapshot.achievements = [{ achievementId: "snapshot-only", title: "快照成就", description: "由服务端返回" }];
    snapshot.skillProgress.dictation = { label: "听写进度", value: "91%", detail: "服务端听写快照" };
    snapshot.skillProgress.choice = { label: "选择进度", value: "73%", detail: "服务端选择快照" };

    renderStateApp(state, "/stats");

    expect(await screen.findByText("4,321")).toBeVisible();
    expect(screen.getByText("777")).toBeVisible();
    expect(screen.getByText("87.6%")).toBeVisible();
    expect(screen.getByText("23 天")).toBeVisible();
    expect(screen.getByLabelText("2025-08-01，学习 9 次，强度 3")).toBeVisible();
    expect(screen.getByText("快照成就")).toBeVisible();
    expect(screen.getByText("91%")).toBeVisible();
    expect(screen.getByText("服务端选择快照")).toBeVisible();
  });

  test("测验后的同一统计快照可展示且通过 storage 恢复", async () => {
    const first = renderScenarioApp("after-quiz", "/stats");
    const snapshot = first.store.read().planStates["plan-core"].stats;

    expect(await screen.findByText(snapshot.skillProgress.dictation.value)).toBeVisible();
    first.unmount();
    window.localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(first.store.read()));
    const restored = createDemoStateStore({ storage: window.localStorage }).read();
    renderStateApp(restored, "/stats");

    expect(await screen.findByText(snapshot.skillProgress.dictation.value)).toBeVisible();
    expect(screen.getByText(snapshot.totalReviewed.toLocaleString("zh-CN"))).toBeVisible();
  });

  test("统计数组被截断时恢复固定种子", () => {
    const state = createDemoState();
    state.planStates["plan-core"].stats.heatmapDays = [];
    state.planStates["plan-core"].today.masteredCount = 999;
    window.localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(state));

    const restored = createDemoStateStore({ storage: window.localStorage });

    expect(restored.read().planStates["plan-core"].today.masteredCount).toBe(126);
    expect(restored.read().planStates["plan-core"].stats.heatmapDays).toHaveLength(12);
  });
});
