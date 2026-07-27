import { beforeEach, describe, expect, test } from "vitest";
import { createDemoState } from "@/data/mock/createDemoState";
import { DEMO_STORAGE_KEY } from "@/data/mock/DemoStateStore";
import { bootstrapDemoScenario } from "./demoScenarioBootstrap";

describe("开发演示场景启动", () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.history.replaceState(null, "", "/");
  });

  test("用白名单场景覆盖旧状态并跳转到白名单内页面", () => {
    window.localStorage.setItem(
      DEMO_STORAGE_KEY,
      JSON.stringify(createDemoState("mutated"))
    );
    window.history.replaceState(
      null,
      "",
      "/__demo/scenario/empty-today?next=/today"
    );

    expect(
      bootstrapDemoScenario({
        isDev: true,
        location: window.location,
        history: window.history,
        storage: window.localStorage
      })
    ).toBe(true);

    const state = JSON.parse(
      window.localStorage.getItem(DEMO_STORAGE_KEY) ?? "{}"
    ) as ReturnType<typeof createDemoState>;
    expect(state.planStates["plan-core"].today.tasks).toEqual([]);
    expect(
      state.planStates["plan-core"].media.byCardId["card-sustainable"]
        .stainLevel
    ).toBe(0);
    expect(window.location.pathname).toBe("/today");
  });

  test("拒绝外部 next 并回退到当前场景的安全默认页", () => {
    window.history.replaceState(
      null,
      "",
      "/__demo/scenario/logged-out?next=https://example.com"
    );

    bootstrapDemoScenario({
      isDev: true,
      location: window.location,
      history: window.history,
      storage: window.localStorage
    });

    expect(window.location.pathname).toBe("/login");
    expect(window.location.origin).not.toBe("https://example.com");
  });

  test("生产模式和未知场景都不写入调试状态", () => {
    window.history.replaceState(
      null,
      "",
      "/__demo/scenario/configured?next=/today"
    );
    expect(
      bootstrapDemoScenario({
        isDev: false,
        location: window.location,
        history: window.history,
        storage: window.localStorage
      })
    ).toBe(false);
    expect(window.localStorage.getItem(DEMO_STORAGE_KEY)).toBeNull();

    window.history.replaceState(null, "", "/__demo/scenario/not-real");
    expect(
      bootstrapDemoScenario({
        isDev: true,
        location: window.location,
        history: window.history,
        storage: window.localStorage
      })
    ).toBe(false);
    expect(window.localStorage.getItem(DEMO_STORAGE_KEY)).toBeNull();
  });
});
