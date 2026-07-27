import type { AuthSession } from "@/domain/auth";
import type { Book, LearningPlan } from "@/domain/books";
import type { WordGroup } from "@/domain/groups";
import type { LearningCard, StudySession } from "@/domain/learning";
import type { CardMedia } from "@/domain/media";
import type { AppSettings } from "@/domain/settings";
import type { StatsSummary } from "@/domain/stats";
import type { TodaySummary } from "@/domain/today";

export type DemoScenario =
  | "logged-out"
  | "configured"
  | "empty-today"
  | "empty-books"
  | "quiz-complete"
  | "quiz-dictation"
  | "quiz-choice"
  | "after-quiz"
  | "mutated";

/** 与单个学习计划绑定的资源，切换计划时不丢弃历史分区。 */
export interface PlanDemoState {
  groups: { items: WordGroup[] };
  cards: { byCardId: Record<string, LearningCard>; byWordKey: Record<string, LearningCard> };
  today: TodaySummary;
  study: { sessions: Record<string, StudySession> };
  quiz: { mode: "dictation" | "choice" | null };
  media: { byCardId: Record<string, CardMedia> };
  stats: StatsSummary;
}

export interface DemoState {
  schemaVersion: 1;
  clock: { today: "2026-07-23" };
  auth: { session: AuthSession | null };
  settings: AppSettings;
  books: { activePlanId: string | null; plans: LearningPlan[]; items: Book[] };
  planStates: Record<string, PlanDemoState>;
}

const sustainableCard: LearningCard = {
  cardId: "card-sustainable",
  wordKey: "sustainable",
  headword: "sustainable",
  definition: "可持续的",
  progress: {
    dictation: { skill: "dictation", state: "fuzzy", stability: 12, heatLevel: 2, lastQuizSucceeded: false },
    choice: { skill: "choice", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false }
  }
};

const resilientCard: LearningCard = {
  cardId: "card-resilient",
  wordKey: "resilient",
  headword: "resilient",
  definition: "有韧性的",
  progress: {
    dictation: { skill: "dictation", state: "unlearned", stability: 1, heatLevel: 0, lastQuizSucceeded: false },
    choice: { skill: "choice", state: "fuzzy", stability: 6, heatLevel: 1, lastQuizSucceeded: true }
  }
};

function createPlanState(cardSource: LearningCard, group: WordGroup, today: TodaySummary): PlanDemoState {
  const card = structuredClone(cardSource);
  return {
    groups: { items: [{ ...group, cardIds: [...group.cardIds] }] },
    cards: { byCardId: { [card.cardId]: card }, byWordKey: { [card.wordKey]: card } },
    today,
    study: { sessions: { demo: { sessionId: "demo", status: "active" } } },
    quiz: { mode: null },
    media: { byCardId: { [card.cardId]: { cardId: card.cardId, imageUrl: null, stainLevel: 0 } } },
    stats: { totalReviewed: 842, retentionRate: 0.9, streakDays: 14 }
  };
}

/** 创建可重复的展示数据；数值来自固定服务端快照，不代表客户端计算结果。 */
export function createDemoState(scenario: DemoScenario = "configured"): DemoState {
  const corePlan: LearningPlan = { planId: "plan-core", bookId: "book-core", title: "核心词汇" };
  const advancedPlan: LearningPlan = { planId: "plan-advanced", bookId: "book-advanced", title: "进阶词汇" };
  const state: DemoState = {
    schemaVersion: 1,
    clock: { today: "2026-07-23" },
    auth: {
      session:
        scenario === "logged-out"
          ? null
          : { userId: "demo-user", displayName: "演示用户", authenticated: true }
    },
    settings: { soundEnabled: true, reducedMotion: false, groupSize: 20 },
    books: {
      activePlanId: corePlan.planId,
      plans: [corePlan, advancedPlan],
      items: [
        { bookId: "book-ielts", title: "雅思核心词汇", cardCount: 3000 },
        { bookId: corePlan.bookId, title: corePlan.title, cardCount: 300 },
        { bookId: advancedPlan.bookId, title: advancedPlan.title, cardCount: 180 }
      ]
    },
    planStates: {
      [corePlan.planId]: createPlanState(
        sustainableCard,
        { groupId: "group-focus", name: "重点复习", cardIds: [sustainableCard.cardId] },
        { dueCount: 24, masteredCount: 126, reviewedCount: 18 }
      ),
      [advancedPlan.planId]: createPlanState(
        resilientCard,
        { groupId: "group-advanced", name: "进阶复习", cardIds: [resilientCard.cardId] },
        { dueCount: 9, masteredCount: 42, reviewedCount: 6 }
      )
    }
  };

  const activePlanId = state.books.activePlanId;
  const activePlan = activePlanId ? state.planStates[activePlanId] : undefined;
  if (!activePlan) {
    throw new Error("固定演示数据缺少当前学习计划分区");
  }
  if (scenario === "empty-today") {
    activePlan.today = { dueCount: 0, masteredCount: 126, reviewedCount: 0 };
  }
  if (scenario === "empty-books") {
    state.books = { activePlanId: null, plans: [], items: [] };
    state.planStates = {};
  }
  if (scenario === "quiz-complete") {
    activePlan.study.sessions.demo.status = "completed";
  }
  if (scenario === "quiz-dictation" || scenario === "quiz-choice") {
    activePlan.quiz.mode = scenario === "quiz-dictation" ? "dictation" : "choice";
  }
  if (scenario === "after-quiz") {
    activePlan.today = { dueCount: 23, masteredCount: 127, reviewedCount: 19 };
  }
  if (scenario === "mutated") {
    activePlan.media.byCardId[sustainableCard.cardId].stainLevel = 2;
  }

  return state;
}
