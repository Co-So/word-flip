import type { RepositoryBundle } from "@/data/contracts/RepositoryBundle";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { PlanDemoState } from "@/data/mock/createDemoState";
import type { AppError } from "@/data/contracts/AppError";
import type { PrecomputedQuizResult } from "@/domain/quiz";
import { MockAuthRepository } from "@/data/mock/repositories/MockAuthRepository";
import { MockBookRepository } from "@/data/mock/repositories/MockBookRepository";
import { MockGroupRepository } from "@/data/mock/repositories/MockGroupRepository";
import { MockSettingsRepository } from "@/data/mock/repositories/MockSettingsRepository";
import { MockTodayRepository } from "@/data/mock/repositories/MockTodayRepository";

export const FIXED_DICTATION_RESULT: PrecomputedQuizResult = {
  wordKey: "sustainable",
  skill: "dictation",
  next: { skill: "dictation", state: "fuzzy", stability: 30, heatLevel: 1, lastQuizSucceeded: true },
  dashboardSnapshot: {
    dueCount: 23,
    masteredCount: 127,
    reviewedCount: 19,
    completionRate: 76,
    currentBookTitle: "核心词汇",
    recentStudy: [
      { cardId: "card-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" },
      { cardId: "card-urban", headword: "urban", definition: "城市的", reviewedAtLabel: "26 分钟前" },
      { cardId: "card-ecology", headword: "ecology", definition: "生态学", reviewedAtLabel: "昨天" }
    ],
    tasks: [
      { taskId: "task-review", title: "到期复习", description: "23 张卡片等待巩固" },
      { taskId: "task-group", title: "继续第 12 组", description: "城市与环境主题" }
    ]
  },
  statsSnapshot: { totalReviewed: 843, retentionRate: 0.901, streakDays: 14 }
};

export const FIXED_ADVANCED_DICTATION_RESULT: PrecomputedQuizResult = {
  wordKey: "resilient",
  skill: "dictation",
  next: { skill: "dictation", state: "fuzzy", stability: 4, heatLevel: 1, lastQuizSucceeded: true },
  dashboardSnapshot: {
    dueCount: 8,
    masteredCount: 43,
    reviewedCount: 7,
    completionRate: 44,
    currentBookTitle: "进阶词汇",
    recentStudy: [
      { cardId: "card-resilient", headword: "resilient", definition: "有韧性的", reviewedAtLabel: "刚刚" }
    ],
    tasks: [{ taskId: "task-advanced", title: "进阶复习", description: "8 张卡片等待巩固" }]
  },
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
    auth: new MockAuthRepository(store),
    settings: new MockSettingsRepository(store),
    books: new MockBookRepository(store),
    groups: new MockGroupRepository(store),
    today: new MockTodayRepository(store),
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
