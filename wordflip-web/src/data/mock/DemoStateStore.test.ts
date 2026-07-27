import { describe, expect, test } from "vitest";
import { createMockRepositoryBundle, FIXED_DICTATION_RESULT } from "@/data/mock/fixtures";
import { createDemoStateStore } from "@/data/mock/DemoStateStore";

function createStore() {
  return createDemoStateStore();
}

function currentPlanState(store: ReturnType<typeof createStore>) {
  const state = store.read();
  return state.planStates[state.books.activePlanId!];
}

describe("DemoStateStore", () => {
  test("学习完成不改变任一 skill 的掌握度", () => {
    const store = createStore();
    const before = currentPlanState(store).cards.byWordKey.sustainable.progress;

    store.update((draft) => {
      draft.planStates[draft.books.activePlanId!].study.sessions.demo.status = "completed";
    });

    expect(currentPlanState(store).cards.byWordKey.sustainable.progress).toEqual(before);
  });

  test("测验结果只更新指定 skill", () => {
    const store = createStore();
    const beforeChoice = currentPlanState(store).cards.byWordKey.sustainable.progress.choice;

    store.applyQuizResult(FIXED_DICTATION_RESULT);

    expect(currentPlanState(store).cards.byWordKey.sustainable.progress.choice).toEqual(beforeChoice);
    expect(currentPlanState(store).cards.byWordKey.sustainable.progress.dictation.stability).toBe(30);
  });

  test("重置后恢复固定日期和标准统计", () => {
    const store = createStore();

    store.update((draft) => {
      draft.planStates[draft.books.activePlanId!].today.masteredCount = 999;
    });
    store.reset();

    expect(store.read().clock.today).toBe("2026-07-23");
    expect(currentPlanState(store).today.masteredCount).toBe(126);
  });

  test("只回放服务端预计算的快照，不在模拟层计算统计", () => {
    const store = createStore();

    store.applyQuizResult(FIXED_DICTATION_RESULT);

    expect(currentPlanState(store).today).toEqual(FIXED_DICTATION_RESULT.dashboardSnapshot);
    expect(currentPlanState(store).stats).toEqual(FIXED_DICTATION_RESULT.statsSnapshot);
  });

  test("遇到不兼容的持久化版本时恢复固定种子", () => {
    window.localStorage.setItem("wordflip.web.demo.v1", JSON.stringify({ schemaVersion: 0 }));
    const store = createStore();

    expect(store.read().schemaVersion).toBe(1);
    expect(store.read().clock.today).toBe("2026-07-23");
  });

  test("切换计划只读取当前计划数据，切回后保留历史分组变更", async () => {
    const store = createStore();
    const repositories = createMockRepositoryBundle(store);

    await repositories.groups.appendMembers("group-focus", ["card-core-added"]);
    await repositories.books.switchActivePlan("plan-advanced");

    expect((await repositories.groups.listGroups())[0].cardIds).not.toContain("card-core-added");
    await repositories.groups.appendMembers("group-advanced", ["card-advanced-added"]);
    await repositories.books.switchActivePlan("plan-core");

    expect((await repositories.groups.listGroups())[0].cardIds).toContain("card-core-added");
    await repositories.books.switchActivePlan("plan-advanced");
    expect((await repositories.groups.listGroups())[0].cardIds).toContain("card-advanced-added");
  });

  test("使用真实 activePlanId 能在两个计划之间往返切换", async () => {
    const store = createStore();
    const repositories = createMockRepositoryBundle(store);
    const original = await repositories.books.getActivePlan();

    const advanced = await repositories.books.switchActivePlan("plan-advanced");
    const restored = await repositories.books.switchActivePlan(original!.planId);

    expect(advanced.planId).toBe("plan-advanced");
    expect(restored.planId).toBe(original!.planId);
  });

  test("同版本但截断的持久化状态会恢复固定种子", () => {
    window.localStorage.setItem(
      "wordflip.web.demo.v1",
      JSON.stringify({ schemaVersion: 1, cards: { byCardId: {} } })
    );

    const store = createStore();

    expect(store.read().clock.today).toBe("2026-07-23");
    expect(JSON.parse(window.localStorage.getItem("wordflip.web.demo.v1") ?? "{}").schemaVersion).toBe(1);
  });

  test("拒绝 skill 与服务端结果轨道不一致的预计算结果", () => {
    const store = createStore();
    const mismatched = {
      ...FIXED_DICTATION_RESULT,
      next: { ...FIXED_DICTATION_RESULT.next, skill: "choice" as const }
    };

    try {
      store.applyQuizResult(mismatched);
      expect.unreachable("skill 不匹配的预计算结果必须被拒绝");
    } catch (error) {
      expect(error).toMatchObject({ kind: "validation" });
    }

    expect(currentPlanState(store).cards.byWordKey.sustainable.progress.dictation.skill).toBe("dictation");
  });

  test("测验仓储回放服务端预计算结果而不在 Web 端判题", async () => {
    const store = createStore();
    const repositories = createMockRepositoryBundle(store);

    const response = await repositories.quiz.submitAnswer({
      sessionId: "quiz-demo",
      requestId: "request-demo",
      cardId: "card-sustainable",
      answer: "sustainable"
    });

    expect(response.requestId).toBe("request-demo");
    expect(response.precomputed.next.stability).toBe(30);
    expect(currentPlanState(store).cards.byWordKey.sustainable.progress.dictation.stability).toBe(30);
  });
});
