import type { AppError } from "@/data/contracts/AppError";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { PlanDemoState } from "@/data/mock/createDemoState";
import type { LearningPlan } from "@/domain/books";
import type { AppSettings, OnboardingInput, SettingsRepository } from "@/domain/settings";

interface OnboardingSnapshot {
  plan: LearningPlan;
  planState: PlanDemoState;
}

const afterOnboardingSnapshots: Record<string, OnboardingSnapshot> = {
  "book-ielts": {
    plan: { planId: "plan-ielts", bookId: "book-ielts", title: "雅思核心词汇" },
    planState: {
      groups: { items: [{ groupId: "group-ielts-01", name: "第 1 组", cardIds: ["card-ielts-sustainable"] }] },
      cards: {
        byCardId: {
          "card-ielts-sustainable": {
            cardId: "card-ielts-sustainable", wordKey: "sustainable", headword: "sustainable", definition: "可持续的",
            progress: {
              dictation: { skill: "dictation", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false },
              choice: { skill: "choice", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false }
            }
          }
        },
        byWordKey: {
          sustainable: {
            cardId: "card-ielts-sustainable", wordKey: "sustainable", headword: "sustainable", definition: "可持续的",
            progress: {
              dictation: { skill: "dictation", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false },
              choice: { skill: "choice", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false }
            }
          }
        }
      },
      today: { dueCount: 20, masteredCount: 0, reviewedCount: 0 },
      study: { sessions: {} },
      quiz: { mode: null },
      media: { byCardId: { "card-ielts-sustainable": { cardId: "card-ielts-sustainable", imageUrl: null, stainLevel: 0 } } },
      stats: { totalReviewed: 0, retentionRate: 0, streakDays: 0 }
    }
  }
};

function validation(message: string): AppError {
  return { kind: "validation", message, fieldErrors: {} };
}

/** 首次设置只原子回放固定服务端快照，不在 Web 端计算分组、今日任务或 FSRS。 */
export class MockSettingsRepository implements SettingsRepository {
  constructor(private readonly store: DemoStateStore) {}

  getSettings(): Promise<AppSettings> {
    return Promise.resolve(this.store.read().settings);
  }

  updateSettings(settings: AppSettings): Promise<AppSettings> {
    this.store.update((draft) => {
      draft.settings = structuredClone(settings);
    });
    return Promise.resolve(this.store.read().settings);
  }

  saveOnboarding(input: OnboardingInput): Promise<LearningPlan> {
    const snapshot = afterOnboardingSnapshots[input.bookId];
    if (!snapshot || ![10, 20, 30, 50].includes(input.groupSize) || input.groupStrategy !== "book_order") {
      return Promise.reject(validation("首次设置参数无效"));
    }
    this.store.update((draft) => {
      const plan = structuredClone(snapshot.plan);
      // 只追加新计划并切换唯一当前指针，已有历史计划与分区保持不变。
      if (!draft.books.plans.some((item) => item.planId === plan.planId)) {
        draft.books.plans.push(plan);
      }
      draft.planStates[plan.planId] = structuredClone(snapshot.planState);
      draft.books.activePlanId = plan.planId;
      draft.settings.groupSize = input.groupSize;
    });
    return Promise.resolve(structuredClone(snapshot.plan));
  }
}
