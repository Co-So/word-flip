import { describe, expect, test } from "vitest";
import { FIXED_DICTATION_RESULT } from "@/data/mock/fixtures";
import { createDemoStateStore } from "@/data/mock/DemoStateStore";

function createStore() {
  return createDemoStateStore();
}

describe("DemoStateStore", () => {
  test("学习完成不改变任一 skill 的掌握度", () => {
    const store = createStore();
    const before = store.read().cards.byWordKey.sustainable.progress;

    store.update((draft) => {
      draft.study.sessions.demo.status = "completed";
    });

    expect(store.read().cards.byWordKey.sustainable.progress).toEqual(before);
  });

  test("测验结果只更新指定 skill", () => {
    const store = createStore();
    const beforeChoice = store.read().cards.byWordKey.sustainable.progress.choice;

    store.applyQuizResult(FIXED_DICTATION_RESULT);

    expect(store.read().cards.byWordKey.sustainable.progress.choice).toEqual(beforeChoice);
    expect(store.read().cards.byWordKey.sustainable.progress.dictation.stability).toBe(30);
  });

  test("重置后恢复固定日期和标准统计", () => {
    const store = createStore();

    store.update((draft) => {
      draft.today.masteredCount = 999;
    });
    store.reset();

    expect(store.read().clock.today).toBe("2026-07-23");
    expect(store.read().today.masteredCount).toBe(126);
  });

  test("只回放服务端预计算的快照，不在模拟层计算统计", () => {
    const store = createStore();

    store.applyQuizResult(FIXED_DICTATION_RESULT);

    expect(store.read().today).toEqual(FIXED_DICTATION_RESULT.dashboardSnapshot);
    expect(store.read().stats).toEqual(FIXED_DICTATION_RESULT.statsSnapshot);
  });

  test("遇到不兼容的持久化版本时恢复固定种子", () => {
    window.localStorage.setItem("wordflip.web.demo.v1", JSON.stringify({ schemaVersion: 0 }));
    const store = createStore();

    expect(store.read().schemaVersion).toBe(1);
    expect(store.read().clock.today).toBe("2026-07-23");
  });
});
