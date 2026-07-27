import { createMockRepositoryBundle } from "@/data/mock/fixtures";
import { createDemoStateStore } from "@/data/mock/DemoStateStore";
import { createDemoState } from "@/data/mock/createDemoState";

test("配置完成的用户退出后重新登录仍保留当前计划", async () => {
  const store = createDemoStateStore({ initialState: createDemoState("configured"), storage: null });
  const repositories = createMockRepositoryBundle(store);
  const activePlanId = store.read().books.activePlanId;
  const historicalPlanIds = store.read().books.plans.map((plan) => plan.planId);

  await repositories.auth.signOut();
  await repositories.auth.signIn({ email: "demo@wordflip.local", password: "wordflip-demo" });

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
  store.update((draft) => {
    draft.planStates[plan.planId].groups.items[0].cardIds.push("card-history-sentinel");
    draft.planStates[plan.planId].today.reviewedCount = 99;
  });
  await repositories.settings.saveOnboarding(input);

  const restored = store.read().planStates[plan.planId];
  expect(restored.groups.items[0].cardIds).toContain("card-history-sentinel");
  expect(restored.today.reviewedCount).toBe(99);
});
