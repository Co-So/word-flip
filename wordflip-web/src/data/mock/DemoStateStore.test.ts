import { describe, expect, test } from "vitest";
import { createMockRepositoryBundle, FIXED_DICTATION_RESULT } from "@/data/mock/fixtures";
import { createDemoStateStore, DEMO_STORAGE_KEY } from "@/data/mock/DemoStateStore";
import { createDemoState } from "@/data/mock/createDemoState";
import { QUIZ_FIXTURES } from "@/data/mock/quizFixtures";

function createStore() {
  return createDemoStateStore();
}

function currentPlanState(store: ReturnType<typeof createStore>) {
  const state = store.read();
  return state.planStates[state.books.activePlanId!];
}

async function submittedState() {
  const store = createDemoStateStore({ initialState: createDemoState(), storage: null });
  await createMockRepositoryBundle(store).quiz.submitAnswer({
    sessionId: "quiz-dictation-1",
    requestId: "request-schema-cross-index",
    questionId: "question-dictation-sustainable",
    cardId: "card-sustainable",
    answer: "sustainable"
  });
  return store.read();
}

describe("DemoStateStore", () => {
  test("三计划双轨正误与两种范围均拥有独立静态预计算快照", () => {
    const combinations = new Set(
      QUIZ_FIXTURES.map((fixture) =>
        [
          fixture.planId,
          fixture.precomputed.skill,
          fixture.precomputed.correct ? "correct" : "wrong",
          fixture.scope
        ].join(":")
      )
    );

    expect(QUIZ_FIXTURES).toHaveLength(24);
    expect(combinations.size).toBe(24);
    for (const fixture of QUIZ_FIXTURES) {
      expect(fixture.precomputed.sessionSnapshot.scope).toBe(fixture.scope);
      expect(fixture.precomputed.sessionSnapshot.question.cardId).toBe(fixture.cardId);
    }
  });

  test("学习完成不改变 cardId 下任一 skill 的掌握度", async () => {
    const store = createStore();
    const repositories = createMockRepositoryBundle(store);
    const before = structuredClone(
      currentPlanState(store).cards.byCardId["card-sustainable"].progress
    );

    await repositories.study.completeSession("study-demo");

    expect(currentPlanState(store).cards.byCardId["card-sustainable"].progress).toEqual(before);
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
      draft.planStates[draft.books.activePlanId!].today.stats.masteredCount = 999;
    });
    store.reset();

    expect(store.read().clock.today).toBe("2026-07-23");
    expect(currentPlanState(store).today.stats.masteredCount).toBe(126);
  });

  test("只回放服务端预计算的快照，不在模拟层计算统计", () => {
    const store = createStore();

    store.applyQuizResult(FIXED_DICTATION_RESULT);

    expect(currentPlanState(store).today).toEqual(FIXED_DICTATION_RESULT.dashboardSnapshot);
    expect(currentPlanState(store).stats).toEqual(FIXED_DICTATION_RESULT.statsSnapshot);
  });

  test("遇到不兼容的持久化版本时恢复固定种子", () => {
    window.localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify({ schemaVersion: 1 }));
    const store = createStore();

    expect(store.read().schemaVersion).toBe(5);
    expect(store.read().clock.today).toBe("2026-07-23");
  });

  test("旧 v4 存储明确重置为 v5 固定种子", () => {
    window.localStorage.clear();
    const legacyV4 = { ...createDemoState(), schemaVersion: 4 };
    legacyV4.planStates["plan-core"].today.stats.masteredCount = 999;
    window.localStorage.setItem("wordflip.web.demo.v4", JSON.stringify(legacyV4));

    const store = createStore();
    const persistedV5 = JSON.parse(window.localStorage.getItem(DEMO_STORAGE_KEY) ?? "{}");

    expect(store.read().schemaVersion).toBe(5);
    expect(currentPlanState(store).today.stats.masteredCount).toBe(126);
    expect(persistedV5.schemaVersion).toBe(5);
    expect(window.localStorage.getItem("wordflip.web.demo.v4")).toBeNull();
  });

  test("切换计划只读取当前计划数据，切回后保留历史分组变更", async () => {
    const store = createStore();
    const repositories = createMockRepositoryBundle(store);

    await repositories.groups.appendMembers("group-12", ["card-core-added"]);
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
      DEMO_STORAGE_KEY,
      JSON.stringify({ schemaVersion: 5, cards: { byCardId: {} } })
    );

    const store = createStore();

    expect(store.read().clock.today).toBe("2026-07-23");
    expect(JSON.parse(window.localStorage.getItem(DEMO_STORAGE_KEY) ?? "{}").schemaVersion).toBe(5);
  });

  test("同版本 bookProgress 数值越界时恢复固定种子", () => {
    const mutations: Array<(state: ReturnType<typeof createDemoState>) => void> = [
      (state) => { state.planStates["plan-core"].bookProgress.masteredCount = -1; },
      (state) => { state.planStates["plan-core"].bookProgress.assignedCardCount = 1.5; },
      (state) => { state.planStates["plan-core"].bookProgress.completionPercent = 101; },
      (state) => {
        state.planStates["plan-core"].bookProgress.masteredCount = 301;
        state.planStates["plan-core"].bookProgress.assignedCardCount = 300;
      }
    ];

    for (const mutate of mutations) {
      const persisted = createDemoState();
      mutate(persisted);
      persisted.planStates["plan-core"].today.stats.masteredCount = 999;
      window.localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(persisted));

      const restored = createStore();

      expect(currentPlanState(restored).bookProgress).toEqual({
        masteredCount: 126,
        assignedCardCount: 300,
        completionPercent: 42
      });
      expect(currentPlanState(restored).today.stats.masteredCount).toBe(126);
    }
  });

  test("同版本但学习完成快照截断时恢复固定种子", () => {
    const persisted = createDemoState();
    const plan = persisted.planStates["plan-core"];
    plan.study.afterStudySession = {} as typeof plan.study.afterStudySession;
    plan.today.stats.masteredCount = 999;
    window.localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(persisted));

    const store = createStore();

    expect(currentPlanState(store).today.stats.masteredCount).toBe(126);
  });

  test("同版本但测验幂等状态截断时恢复固定种子", () => {
    const persisted = createDemoState();
    const plan = persisted.planStates["plan-core"];
    plan.quiz.idempotency = [{}] as typeof plan.quiz.idempotency;
    plan.today.stats.masteredCount = 999;
    window.localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(persisted));

    const store = createStore();

    expect(currentPlanState(store).quiz.idempotency).toEqual([]);
    expect(currentPlanState(store).today.stats.masteredCount).toBe(126);
  });

  test("测验结果与幂等记录持久化后可恢复并安全重试", async () => {
    const firstStore = createDemoStateStore({ initialState: createDemoState(), storage: window.localStorage });
    const firstQuiz = createMockRepositoryBundle(firstStore).quiz;
    const submission = {
      sessionId: "quiz-dictation-1",
      requestId: "request-persisted",
      questionId: "question-dictation-sustainable",
      cardId: "card-sustainable",
      answer: "sustainable"
    };
    const first = await firstQuiz.submitAnswer(submission);

    const restoredStore = createDemoStateStore({ storage: window.localStorage });
    const beforeRetry = restoredStore.read();
    const retried = await createMockRepositoryBundle(restoredStore).quiz.submitAnswer(submission);

    expect(retried).toEqual(first);
    expect(restoredStore.read()).toEqual(beforeRetry);
    expect(currentPlanState(restoredStore).quiz.results["quiz-dictation-1"]).toBeDefined();
    expect(currentPlanState(restoredStore).quiz.idempotency).toHaveLength(1);
  });

  test("同一固定 session 重开后保留历史幂等响应并以最后一次快照恢复", async () => {
    window.localStorage.removeItem(DEMO_STORAGE_KEY);
    const store = createDemoStateStore({
      initialState: createDemoState(),
      storage: window.localStorage
    });
    const quiz = createMockRepositoryBundle(store).quiz;
    const first = await quiz.submitAnswer({
      sessionId: "quiz-dictation-1",
      requestId: "request-history-correct",
      questionId: "question-dictation-sustainable",
      cardId: "card-sustainable",
      answer: "sustainable"
    });
    await quiz.createSession("dictation", "current-plan");
    const latest = await quiz.submitAnswer({
      sessionId: "quiz-dictation-1",
      requestId: "request-history-wrong",
      questionId: "question-dictation-sustainable",
      cardId: "card-sustainable",
      answer: "not-sustainable"
    });

    expect(first.correct).toBe(true);
    expect(latest.correct).toBe(false);
    expect(currentPlanState(store).quiz.idempotency).toHaveLength(2);

    const restored = createDemoStateStore({ storage: window.localStorage });
    const restoredPlan = currentPlanState(restored);

    expect(restoredPlan.quiz.idempotency.map((record) => record.response.correct)).toEqual([
      true,
      false
    ]);
    expect(restoredPlan.quiz.sessions["quiz-dictation-1"]).toEqual(
      latest.precomputed.sessionSnapshot
    );
    expect(restoredPlan.quiz.results["quiz-dictation-1"]).toEqual(
      latest.precomputed.resultSnapshot
    );
    expect(restoredPlan.cards.byCardId["card-sustainable"].progress.dictation).toEqual(
      latest.precomputed.next
    );
  });

  test("固定 session 重开但尚未再次提交时保留历史且不伪造当前 result", async () => {
    window.localStorage.removeItem(DEMO_STORAGE_KEY);
    const store = createDemoStateStore({
      initialState: createDemoState(),
      storage: window.localStorage
    });
    const quiz = createMockRepositoryBundle(store).quiz;
    const historical = await quiz.submitAnswer({
      sessionId: "quiz-dictation-1",
      requestId: "request-history-before-active",
      questionId: "question-dictation-sustainable",
      cardId: "card-sustainable",
      answer: "sustainable"
    });
    await quiz.createSession("dictation", "current-plan");

    const restored = createDemoStateStore({ storage: window.localStorage });
    const restoredPlan = currentPlanState(restored);

    expect(restoredPlan.quiz.idempotency).toHaveLength(1);
    expect(restoredPlan.quiz.sessions["quiz-dictation-1"].status).toBe("active");
    expect(restoredPlan.quiz.results["quiz-dictation-1"]).toBeUndefined();
    expect(restoredPlan.cards.byCardId["card-sustainable"].progress.dictation).toEqual(
      historical.precomputed.next
    );
  });

  test("current-plan 完成后以 due-today 重开未提交可恢复旧 scope 历史", async () => {
    window.localStorage.removeItem(DEMO_STORAGE_KEY);
    const store = createDemoStateStore({
      initialState: createDemoState(),
      storage: window.localStorage
    });
    const quiz = createMockRepositoryBundle(store).quiz;
    await quiz.submitAnswer({
      sessionId: "quiz-dictation-1",
      requestId: "request-scope-current-active",
      questionId: "question-dictation-sustainable",
      cardId: "card-sustainable",
      answer: "sustainable"
    });
    await quiz.createSession("dictation", "due-today");

    const restored = createDemoStateStore({ storage: window.localStorage });
    const restoredPlan = currentPlanState(restored);

    expect(restoredPlan.quiz.idempotency).toHaveLength(1);
    expect(restoredPlan.quiz.idempotency[0].scope).toBe("current-plan");
    expect(restoredPlan.quiz.idempotency[0].response.precomputed.sessionSnapshot.scope).toBe(
      "current-plan"
    );
    expect(restoredPlan.quiz.sessions["quiz-dictation-1"]).toMatchObject({
      status: "active",
      scope: "due-today"
    });
    expect(restoredPlan.quiz.results["quiz-dictation-1"]).toBeUndefined();
  });

  test("current-plan 完成后以 due-today 重开并提交可恢复两种 scope 历史", async () => {
    window.localStorage.removeItem(DEMO_STORAGE_KEY);
    const store = createDemoStateStore({
      initialState: createDemoState(),
      storage: window.localStorage
    });
    const quiz = createMockRepositoryBundle(store).quiz;
    await quiz.submitAnswer({
      sessionId: "quiz-dictation-1",
      requestId: "request-scope-current-completed",
      questionId: "question-dictation-sustainable",
      cardId: "card-sustainable",
      answer: "sustainable"
    });
    await quiz.createSession("dictation", "due-today");
    const latest = await quiz.submitAnswer({
      sessionId: "quiz-dictation-1",
      requestId: "request-scope-due-completed",
      questionId: "question-dictation-sustainable",
      cardId: "card-sustainable",
      answer: "not-sustainable"
    });

    const restored = createDemoStateStore({ storage: window.localStorage });
    const restoredPlan = currentPlanState(restored);

    expect(restoredPlan.quiz.idempotency.map((record) => record.scope)).toEqual([
      "current-plan",
      "due-today"
    ]);
    expect(
      restoredPlan.quiz.idempotency.map(
        (record) => record.response.precomputed.sessionSnapshot.scope
      )
    ).toEqual(["current-plan", "due-today"]);
    expect(restoredPlan.quiz.sessions["quiz-dictation-1"]).toEqual(
      latest.precomputed.sessionSnapshot
    );
    expect(restoredPlan.quiz.results["quiz-dictation-1"]).toEqual(
      latest.precomputed.resultSnapshot
    );
    expect(restoredPlan.cards.byCardId["card-sustainable"].progress.dictation).toEqual(
      latest.precomputed.next
    );
  });

  test("测验幂等记录的 ghost session、错 skill/scope/result 会触发固定种子恢复", async () => {
    const mutations: Array<(state: Awaited<ReturnType<typeof submittedState>>) => void> = [
      (state) => {
        const record = state.planStates["plan-core"].quiz.idempotency[0];
        record.sessionId = "quiz-ghost";
        record.response.precomputed.sessionSnapshot.sessionId = "quiz-ghost";
        record.response.precomputed.resultSnapshot.sessionId = "quiz-ghost";
      },
      (state) => {
        const precomputed = state.planStates["plan-core"].quiz.idempotency[0].response.precomputed;
        precomputed.skill = "choice";
        precomputed.next.skill = "choice";
        precomputed.sessionSnapshot.skill = "choice";
        precomputed.sessionSnapshot.question.skill = "choice";
      },
      (state) => {
        state.planStates["plan-core"].quiz.idempotency[0].response.precomputed.sessionSnapshot.scope = "due-today";
      },
      (state) => {
        const result = state.planStates["plan-core"].quiz.idempotency[0].response.precomputed.resultSnapshot;
        result.score = 0;
        result.accuracy = 0;
        result.rating = "keep_going";
      }
    ];

    for (const mutate of mutations) {
      const persisted = await submittedState();
      mutate(persisted);
      window.localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(persisted));

      const restored = createStore();

      expect(currentPlanState(restored).quiz.idempotency).toEqual([]);
      expect(currentPlanState(restored).quiz.results).toEqual({});
      expect(currentPlanState(restored).today.stats.masteredCount).toBe(126);
    }
  });

  test("同版本但 cardId 索引键错误的持久化状态会恢复固定种子", () => {
    const persisted = createDemoState();
    const plan = persisted.planStates["plan-core"];
    plan.cards.byCardId["card-index-mismatch"] = structuredClone(plan.cards.byCardId["card-sustainable"]);
    plan.today.stats.masteredCount = 999;
    window.localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(persisted));

    const store = createStore();

    expect(currentPlanState(store).cards.byCardId).not.toHaveProperty("card-index-mismatch");
    expect(currentPlanState(store).today.stats.masteredCount).toBe(126);
  });

  test("同版本但缺失 wordKey 索引的持久化状态会恢复固定种子", () => {
    const persisted = createDemoState();
    const plan = persisted.planStates["plan-core"];
    delete plan.cards.byWordKey.sustainable;
    plan.today.stats.masteredCount = 999;
    window.localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(persisted));

    const store = createStore();

    expect(currentPlanState(store).today.stats.masteredCount).toBe(126);
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
      sessionId: "quiz-dictation-1",
      requestId: "request-demo",
      questionId: "question-dictation-sustainable",
      cardId: "card-sustainable",
      answer: "sustainable"
    });

    expect(response.requestId).toBe("request-demo");
    expect(response.precomputed.next.stability).toBe(30);
    expect(currentPlanState(store).cards.byWordKey.sustainable.progress.dictation.stability).toBe(30);
  });

  test("切换到进阶计划后，测验仓储只回放该计划卡片的预计算快照", async () => {
    const store = createStore();
    const repositories = createMockRepositoryBundle(store);
    await repositories.books.switchActivePlan("plan-advanced");

    const response = await repositories.quiz.submitAnswer({
      sessionId: "quiz-dictation-1",
      requestId: "request-advanced",
      questionId: "question-dictation-resilient",
      cardId: "card-resilient",
      answer: "resilient"
    });

    expect(response.precomputed.cardId).toBe("card-resilient");
    expect(currentPlanState(store).cards.byCardId["card-resilient"].progress.dictation).toEqual(response.precomputed.next);
  });
});
