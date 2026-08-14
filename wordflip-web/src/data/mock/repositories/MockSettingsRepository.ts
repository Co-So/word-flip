import type { AppError } from "@/data/contracts/AppError";
import { createQuizDemoState } from "@/data/mock/createDemoState";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { PlanDemoState } from "@/data/mock/createDemoState";
import type { LearningPlan } from "@/domain/books";
import type { AppSettings, OnboardingInput, SettingsRepository } from "@/domain/settings";
import { createStatsSnapshot } from "@/data/mock/statsFixtures";

interface OnboardingSnapshot {
  plan: LearningPlan;
  planState: PlanDemoState;
}

const sustainablePresentation = {
  phonetic: "/səˈsteɪnəbl/",
  example: "The city needs a sustainable transport plan.",
  imageDescription: "树木与城市建筑交叠的可持续发展图像占位"
};

const resilientPresentation = {
  phonetic: "/rɪˈzɪliənt/",
  example: "Resilient communities recover together.",
  imageDescription: "风中仍然挺立的树木图像占位"
};

function emptyTodaySnapshot(groupId: string, groupName: string) {
  return {
    date: "2026-07-23",
    streakDays: 0,
    stats: { masteredCount: 0, dueReviewCount: 0, completionPercent: 0 },
    tasks: {
      newWords: {
        count: 20,
        label: "新词",
        sources: [{ groupId, groupName, count: 20 }]
      },
      dueReview: { count: 0, label: "到期复习", sources: [] },
      quiz: { count: 0, label: "测验", sources: [] }
    },
    recommendedStudy: {
      groupId,
      groupName,
      wordCount: 20,
      reason: "new_words" as const
    },
    recentGroups: []
  };
}

function firstStudySession(cardId: string) {
  return {
    sessionId: "study-demo",
    status: "active" as const,
    cardIds: [cardId],
    progressLabel: "0 / 20 WORDS",
    queueSummary: [
      { label: "新词", value: "20" },
      { label: "待巩固", value: "0" },
      { label: "已浏览", value: "0" }
    ]
  };
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
            ...sustainablePresentation,
            progress: {
              dictation: { skill: "dictation", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false },
              choice: { skill: "choice", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false }
            }
          }
        },
        byWordKey: {
          sustainable: {
            cardId: "card-ielts-sustainable", wordKey: "sustainable", headword: "sustainable", definition: "可持续的",
            ...sustainablePresentation,
            progress: {
              dictation: { skill: "dictation", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false },
              choice: { skill: "choice", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false }
            }
          }
        }
      },
      today: emptyTodaySnapshot("group-ielts-01", "第 1 组"),
      bookProgress: { masteredCount: 0, assignedCardCount: 3000, completionPercent: 0 },
      study: {
        sessions: { "study-demo": firstStudySession("card-ielts-sustainable") },
        afterStudySession: emptyTodaySnapshot("group-ielts-01", "第 1 组")
      },
      quiz: createQuizDemoState({
        cardId: "card-ielts-sustainable", wordKey: "sustainable", headword: "sustainable", definition: "可持续的",
        ...sustainablePresentation,
        progress: {
          dictation: { skill: "dictation", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false },
          choice: { skill: "choice", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false }
        }
      }),
      media: {
        byCardId: {
          "card-ielts-sustainable": {
            cardId: "card-ielts-sustainable",
            imageUrl: null,
            stainLevel: 0,
            transform: { rotation: 0, scale: 1, positionX: 0, positionY: 0 }
          }
        }
      },
      stats: createStatsSnapshot(0, 0, 0, 0, "empty")
    }
  },
  "book-core": {
    plan: { planId: "plan-core", bookId: "book-core", title: "核心词汇" },
    planState: {
      groups: { items: [{ groupId: "group-core-01", name: "第 1 组", cardIds: ["card-sustainable"] }] },
      cards: {
        byCardId: {
          "card-sustainable": {
            cardId: "card-sustainable", wordKey: "sustainable", headword: "sustainable", definition: "可持续的",
            ...sustainablePresentation,
            progress: {
              dictation: { skill: "dictation", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false },
              choice: { skill: "choice", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false }
            }
          }
        },
        byWordKey: {
          sustainable: {
            cardId: "card-sustainable", wordKey: "sustainable", headword: "sustainable", definition: "可持续的",
            ...sustainablePresentation,
            progress: {
              dictation: { skill: "dictation", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false },
              choice: { skill: "choice", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false }
            }
          }
        }
      },
      today: emptyTodaySnapshot("group-core-01", "第 1 组"),
      bookProgress: { masteredCount: 0, assignedCardCount: 300, completionPercent: 0 },
      study: {
        sessions: { "study-demo": firstStudySession("card-sustainable") },
        afterStudySession: emptyTodaySnapshot("group-core-01", "第 1 组")
      },
      quiz: createQuizDemoState({
        cardId: "card-sustainable", wordKey: "sustainable", headword: "sustainable", definition: "可持续的",
        ...sustainablePresentation,
        progress: {
          dictation: { skill: "dictation", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false },
          choice: { skill: "choice", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false }
        }
      }),
      media: {
        byCardId: {
          "card-sustainable": {
            cardId: "card-sustainable",
            imageUrl: "/card-images/sustainable.webp",
            stainLevel: 0,
            transform: { rotation: 0, scale: 1, positionX: 0, positionY: 0 }
          }
        }
      },
      stats: createStatsSnapshot(0, 0, 0, 0, "empty")
    }
  },
  "book-advanced": {
    plan: { planId: "plan-advanced", bookId: "book-advanced", title: "进阶词汇" },
    planState: {
      groups: { items: [{ groupId: "group-advanced-01", name: "第 1 组", cardIds: ["card-resilient"] }] },
      cards: {
        byCardId: {
          "card-resilient": {
            cardId: "card-resilient", wordKey: "resilient", headword: "resilient", definition: "有韧性的",
            ...resilientPresentation,
            progress: {
              dictation: { skill: "dictation", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false },
              choice: { skill: "choice", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false }
            }
          }
        },
        byWordKey: {
          resilient: {
            cardId: "card-resilient", wordKey: "resilient", headword: "resilient", definition: "有韧性的",
            ...resilientPresentation,
            progress: {
              dictation: { skill: "dictation", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false },
              choice: { skill: "choice", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false }
            }
          }
        }
      },
      today: emptyTodaySnapshot("group-advanced-01", "第 1 组"),
      bookProgress: { masteredCount: 0, assignedCardCount: 180, completionPercent: 0 },
      study: {
        sessions: { "study-demo": firstStudySession("card-resilient") },
        afterStudySession: emptyTodaySnapshot("group-advanced-01", "第 1 组")
      },
      quiz: createQuizDemoState({
        cardId: "card-resilient", wordKey: "resilient", headword: "resilient", definition: "有韧性的",
        ...resilientPresentation,
        progress: {
          dictation: { skill: "dictation", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false },
          choice: { skill: "choice", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false }
        }
      }),
      media: {
        byCardId: {
          "card-resilient": {
            cardId: "card-resilient",
            imageUrl: null,
            stainLevel: 0,
            transform: { rotation: 0, scale: 1, positionX: 0, positionY: 0 }
          }
        }
      },
      stats: createStatsSnapshot(0, 0, 0, 0, "empty")
    }
  }
};

function validation(message: string): AppError {
  return { kind: "validation", message, fieldErrors: {} };
}

/** 无历史计划时回放已批准固定快照；已有计划只移动 activePlanId 指针。 */
export function activatePrecomputedBookPlan(
  store: DemoStateStore,
  bookId: string,
  groupSize: 10 | 20 | 30 | 50
): LearningPlan {
  const snapshot = afterOnboardingSnapshots[bookId];
  if (!snapshot) {
    throw validation("找不到指定词书的预计算计划");
  }
  store.update((draft) => {
    const plan = structuredClone(snapshot.plan);
    const alreadyExists = draft.books.plans.some((item) => item.planId === plan.planId);
    // 新计划才回放固定服务端快照；已有计划只能激活，绝不能覆盖历史分区。
    if (!alreadyExists) {
      draft.books.plans.push(plan);
      draft.planStates[plan.planId] = structuredClone(snapshot.planState);
    }
    draft.books.activePlanId = plan.planId;
    draft.settings.groupSize = groupSize;
  });
  return structuredClone(snapshot.plan);
}

/** 首次设置只原子回放固定服务端快照，不在 Web 端计算分组、今日任务或 FSRS。 */
export class MockSettingsRepository implements SettingsRepository {
  constructor(private readonly store: DemoStateStore) {}

  supportsDemoReset(): boolean {
    return true;
  }

  getSettings(): Promise<AppSettings> {
    return Promise.resolve(this.store.read().settings);
  }

  updateSettings(settings: AppSettings): Promise<AppSettings> {
    if (![10, 20, 30, 50].includes(settings.groupSize)) {
      return Promise.reject(validation("设置参数无效"));
    }
    this.store.update((draft) => {
      // 保存仅回放这一份明确的 post-save 快照，不在页面推导其他业务状态。
      draft.settings = structuredClone(settings);
    });
    return Promise.resolve(this.store.read().settings);
  }

  saveOnboarding(input: OnboardingInput): Promise<LearningPlan> {
    if (!afterOnboardingSnapshots[input.bookId] || ![10, 20, 30, 50].includes(input.groupSize) || input.groupStrategy !== "book_order") {
      return Promise.reject(validation("首次设置参数无效"));
    }
    return Promise.resolve(activatePrecomputedBookPlan(this.store, input.bookId, input.groupSize));
  }

  resetDemo(): Promise<AppSettings> {
    this.store.reset();
    return Promise.resolve(this.store.read().settings);
  }
}
