import type { RepositoryBundle } from "@/data/contracts/RepositoryBundle";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { PlanDemoState } from "@/data/mock/createDemoState";
import type { AppError } from "@/data/contracts/AppError";
import type { PrecomputedQuizResult } from "@/domain/quiz";

export const FIXED_DICTATION_RESULT: PrecomputedQuizResult = {
  wordKey: "sustainable",
  skill: "dictation",
  next: { skill: "dictation", state: "fuzzy", stability: 30, heatLevel: 1, lastQuizSucceeded: true },
  dashboardSnapshot: { dueCount: 23, masteredCount: 127, reviewedCount: 19 },
  statsSnapshot: { totalReviewed: 843, retentionRate: 0.901, streakDays: 14 }
};

export const FIXED_ADVANCED_DICTATION_RESULT: PrecomputedQuizResult = {
  wordKey: "resilient",
  skill: "dictation",
  next: { skill: "dictation", state: "fuzzy", stability: 4, heatLevel: 1, lastQuizSucceeded: true },
  dashboardSnapshot: { dueCount: 8, masteredCount: 43, reviewedCount: 7 },
  statsSnapshot: { totalReviewed: 843, retentionRate: 0.903, streakDays: 14 }
};

/** 按计划和 cardId 固定的服务端测验快照；skill 由快照预定义，Web 不判题或计算 FSRS。 */
const PRECOMPUTED_QUIZ_RESULTS: Record<string, Record<string, PrecomputedQuizResult>> = {
  "plan-core": { "card-sustainable": FIXED_DICTATION_RESULT },
  "plan-advanced": { "card-resilient": FIXED_ADVANCED_DICTATION_RESULT }
};

function notFound(message: string): AppError {
  return { kind: "not-found", message };
}

function noActivePlan(): AppError {
  return { kind: "conflict", message: "当前没有可用学习计划" };
}

function currentPlan(store: DemoStateStore): PlanDemoState | null {
  return store.readActivePlanState();
}

function precomputedQuizResult(store: DemoStateStore, cardId: string): PrecomputedQuizResult | null {
  const planId = store.read().books.activePlanId;
  return planId ? PRECOMPUTED_QUIZ_RESULTS[planId]?.[cardId] ?? null : null;
}

function unavailable(message: string): AppError {
  return { kind: "unavailable", message, retryable: true };
}

/** 供视觉演示和测试注入的仓储实现；它只回放服务端固定快照，便于替换为 HTTP 仓储。 */
export function createMockRepositoryBundle(store: DemoStateStore): RepositoryBundle {
  return {
    auth: {
      getSession: () => Promise.resolve(store.read().auth.session),
      signOut: () => {
        store.update((draft) => {
          draft.auth.session = null;
        });
        return Promise.resolve({ signedOut: true as const });
      }
    },
    settings: {
      getSettings: () => Promise.resolve(store.read().settings),
      updateSettings: (settings) => {
        store.update((draft) => {
          draft.settings = structuredClone(settings);
        });
        return Promise.resolve(store.read().settings);
      }
    },
    books: {
      listBooks: () => Promise.resolve(store.read().books.items),
      getActivePlan: () => {
        const state = store.read();
        return Promise.resolve(state.books.plans.find((plan) => plan.planId === state.books.activePlanId) ?? null);
      },
      switchActivePlan: (planId) => {
        try {
          return Promise.resolve(store.switchActivePlan(planId));
        } catch (error) {
          return Promise.reject(error);
        }
      }
    },
    groups: {
      listGroups: () => {
        const plan = currentPlan(store);
        return plan ? Promise.resolve(plan.groups.items) : Promise.reject(noActivePlan());
      },
      appendMembers: (groupId, cardIds) => {
        const before = currentPlan(store);
        const group = before?.groups.items.find((item) => item.groupId === groupId);
        if (!group) {
          return Promise.reject(before ? notFound("找不到指定分组") : noActivePlan());
        }
        store.updateActivePlan((draft) => {
          const target = draft.groups.items.find((item) => item.groupId === groupId)!;
          // 分组成员遵循追加语义，不以新数组覆盖现有成员。
          target.cardIds.push(...cardIds.filter((cardId) => !target.cardIds.includes(cardId)));
        });
        return Promise.resolve(currentPlan(store)!.groups.items.find((item) => item.groupId === groupId)!);
      }
    },
    today: {
      getSummary: () => {
        const plan = currentPlan(store);
        return plan ? Promise.resolve(plan.today) : Promise.reject(noActivePlan());
      }
    },
    study: {
      getCards: () => {
        const plan = currentPlan(store);
        return plan ? Promise.resolve(Object.values(plan.cards.byCardId)) : Promise.reject(noActivePlan());
      },
      getSession: (sessionId) => {
        const plan = currentPlan(store);
        const session = plan?.study.sessions[sessionId];
        return session ? Promise.resolve(session) : Promise.reject(plan ? notFound("找不到学习会话") : noActivePlan());
      },
      completeSession: (sessionId) => {
        const plan = currentPlan(store);
        if (!plan?.study.sessions[sessionId]) {
          return Promise.reject(plan ? notFound("找不到学习会话") : noActivePlan());
        }
        // 完成学习仅更新会话展示状态，不写任一 skill 记忆。
        store.updateActivePlan((draft) => {
          draft.study.sessions[sessionId].status = "completed";
        });
        return Promise.resolve(currentPlan(store)!.study.sessions[sessionId]);
      }
    },
    quiz: {
      submitAnswer: (submission) => {
        const plan = currentPlan(store);
        if (!plan?.cards.byCardId[submission.cardId]) {
          return Promise.reject(plan ? notFound("找不到当前计划的学习卡") : noActivePlan());
        }
        const precomputed = precomputedQuizResult(store, submission.cardId);
        if (!precomputed) {
          return Promise.reject(unavailable("当前计划的学习卡没有可回放的服务端预计算结果"));
        }
        try {
          // 仅按当前计划与 cardId 回放服务端快照；此处不读取 answer 计算任何结果。
          const snapshot = structuredClone(precomputed);
          store.applyQuizResult(snapshot);
          return Promise.resolve({ requestId: submission.requestId, accepted: true, precomputed: snapshot });
        } catch (error) {
          return Promise.reject(error);
        }
      }
    },
    media: {
      getMedia: (cardId) => {
        const plan = currentPlan(store);
        const media = plan?.media.byCardId[cardId];
        return media ? Promise.resolve(media) : Promise.reject(plan ? notFound("找不到卡片媒体") : noActivePlan());
      },
      saveMedia: (media) => {
        const plan = currentPlan(store);
        if (!plan?.cards.byCardId[media.cardId]) {
          return Promise.reject(plan ? notFound("找不到当前计划的学习卡") : noActivePlan());
        }
        store.updateActivePlan((draft) => {
          draft.media.byCardId[media.cardId] = structuredClone(media);
        });
        return Promise.resolve(currentPlan(store)!.media.byCardId[media.cardId]);
      }
    },
    stats: {
      getSummary: () => {
        const plan = currentPlan(store);
        return plan ? Promise.resolve(plan.stats) : Promise.reject(noActivePlan());
      }
    }
  };
}
