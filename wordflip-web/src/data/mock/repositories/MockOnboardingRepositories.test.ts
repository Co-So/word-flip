import { createMockRepositoryBundle } from "@/data/mock/fixtures";
import { createDemoStateStore } from "@/data/mock/DemoStateStore";
import { createDemoState } from "@/data/mock/createDemoState";

test("配置完成的用户退出后重新登录仍保留当前计划", async () => {
  const store = createDemoStateStore({ initialState: createDemoState("configured"), storage: null });
  const repositories = createMockRepositoryBundle(store);
  const activePlanId = store.read().books.activePlanId;
  const historicalPlanIds = store.read().books.plans.map((plan) => plan.planId);

  await repositories.auth.signOut();
  await repositories.auth.signIn({ account: "demo@wordflip.local", password: "wordflip-demo" });

  expect(store.read().books.activePlanId).toBe(activePlanId);
  expect(store.read().books.plans.map((plan) => plan.planId)).toEqual(historicalPlanIds);
  expect((await repositories.books.getActivePlan())?.planId).toBe(activePlanId);
});

test("重复保存已有首次设置计划不会覆盖其历史分区", async () => {
  const initialState = createDemoState("configured");
  initialState.books.activePlanId = null;
  initialState.books.plans = [];
  initialState.planStates = {};
  const store = createDemoStateStore({ initialState, storage: null });
  const repositories = createMockRepositoryBundle(store);
  const input = { bookId: "book-ielts", groupSize: 30 as const, groupStrategy: "book_order" as const };

  const plan = await repositories.settings.saveOnboarding(input);
  expect((await repositories.study.getSession("study-demo")).cardIds).toEqual([
    "card-ielts-sustainable"
  ]);
  expect((await repositories.quiz.getSession("quiz-dictation-1")).question.cardId).toBe(
    "card-ielts-sustainable"
  );
  expect((await repositories.quiz.getSession("quiz-choice-1")).skill).toBe("choice");
  store.update((draft) => {
    draft.planStates[plan.planId].groups.items[0].cardIds.push("card-history-sentinel");
    draft.planStates[plan.planId].today.tasks.quiz.count = 99;
  });
  await repositories.settings.saveOnboarding(input);

  const restored = store.read().planStates[plan.planId];
  expect(restored.groups.items[0].cardIds).toContain("card-history-sentinel");
  expect(restored.today.tasks.quiz.count).toBe(99);
});

test.each([
  ["book-ielts", "card-ielts-sustainable", "question-dictation-sustainable", "sustainable"],
  ["book-core", "card-sustainable", "question-dictation-sustainable", "sustainable"],
  ["book-advanced", "card-resilient", "question-dictation-resilient", "resilient"]
] as const)("新建 %s 计划时同步种子测验会话与预计算结果", async (bookId, cardId, questionId, answer) => {
  const initialState = createDemoState("configured");
  initialState.books.activePlanId = null;
  initialState.books.plans = [];
  initialState.planStates = {};
  const store = createDemoStateStore({ initialState, storage: null });
  const repositories = createMockRepositoryBundle(store);

  await repositories.settings.saveOnboarding({
    bookId,
    groupSize: 20,
    groupStrategy: "book_order"
  });
  const session = await repositories.quiz.getSession("quiz-dictation-1");
  const response = await repositories.quiz.submitAnswer({
    sessionId: session.sessionId,
    requestId: `request-${bookId}`,
    questionId,
    cardId,
    answer
  });

  expect(session.question.cardId).toBe(cardId);
  expect(response.precomputed.cardId).toBe(cardId);
  const plan = store.readActivePlanState()!;
  expect(plan.media.byCardId[cardId].transform).toEqual({
    rotation: 0,
    scale: 1,
    positionX: 0,
    positionY: 0
  });
  expect(plan.stats.heatmapDays).toHaveLength(12);
  expect(plan.stats.skillProgress).toHaveProperty("dictation");
  expect(plan.stats.skillProgress).toHaveProperty("choice");
});
