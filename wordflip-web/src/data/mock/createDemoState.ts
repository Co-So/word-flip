import type { AuthSession } from "@/domain/auth";
import type { Book, BookProgress, LearningPlan } from "@/domain/books";
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
  bookProgress: BookProgress;
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

const urbanCard: LearningCard = {
  cardId: "card-urban",
  wordKey: "urban",
  headword: "urban",
  definition: "城市的",
  progress: {
    dictation: { skill: "dictation", state: "unknown", stability: 18, heatLevel: 1, lastQuizSucceeded: true },
    choice: { skill: "choice", state: "fuzzy", stability: 9, heatLevel: 1, lastQuizSucceeded: true }
  }
};

const ecologyCard: LearningCard = {
  cardId: "card-ecology",
  wordKey: "ecology",
  headword: "ecology",
  definition: "生态学",
  progress: {
    dictation: { skill: "dictation", state: "fuzzy", stability: 8, heatLevel: 1, lastQuizSucceeded: false },
    choice: { skill: "choice", state: "unknown", stability: 22, heatLevel: 1, lastQuizSucceeded: true }
  }
};

function createPlanState(
  cardSources: LearningCard[],
  group: WordGroup,
  today: TodaySummary,
  bookProgress: BookProgress
): PlanDemoState {
  const cards = cardSources.map((card) => structuredClone(card));
  const byCardId = Object.fromEntries(cards.map((card) => [card.cardId, card]));
  const byWordKey = Object.fromEntries(cards.map((card) => [card.wordKey, card]));
  return {
    groups: { items: [{ ...group, cardIds: [...group.cardIds] }] },
    cards: { byCardId, byWordKey },
    today,
    bookProgress,
    study: { sessions: { demo: { sessionId: "demo", status: "active" } } },
    quiz: { mode: null },
    media: {
      byCardId: Object.fromEntries(
        cards.map((card) => [card.cardId, { cardId: card.cardId, imageUrl: null, stainLevel: 0 }])
      )
    },
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
        [sustainableCard, urbanCard, ecologyCard],
        { groupId: "group-12", name: "第 12 组 · 城市与环境", cardIds: [sustainableCard.cardId] },
        {
          dueCount: 24,
          masteredCount: 126,
          reviewedCount: 18,
          completionRate: 72,
          currentBookTitle: corePlan.title,
          recentStudy: [
            { cardId: sustainableCard.cardId, headword: sustainableCard.headword, definition: sustainableCard.definition, reviewedAtLabel: "8 分钟前" },
            { cardId: urbanCard.cardId, headword: urbanCard.headword, definition: urbanCard.definition, reviewedAtLabel: "26 分钟前" },
            { cardId: ecologyCard.cardId, headword: ecologyCard.headword, definition: ecologyCard.definition, reviewedAtLabel: "昨天" }
          ],
          tasks: [
            { taskId: "task-review", title: "到期复习", description: "24 张卡片等待巩固" },
            { taskId: "task-group", title: "继续第 12 组", description: "城市与环境主题" }
          ]
        },
        { learnedCount: 126, publishedCardCount: 300, completionRate: 42 }
      ),
      [advancedPlan.planId]: createPlanState(
        [resilientCard],
        { groupId: "group-advanced", name: "进阶复习", cardIds: [resilientCard.cardId] },
        {
          dueCount: 9,
          masteredCount: 42,
          reviewedCount: 6,
          completionRate: 38,
          currentBookTitle: advancedPlan.title,
          recentStudy: [
            { cardId: resilientCard.cardId, headword: resilientCard.headword, definition: resilientCard.definition, reviewedAtLabel: "昨天" }
          ],
          tasks: [{ taskId: "task-advanced", title: "进阶复习", description: "9 张卡片等待巩固" }]
        },
        { learnedCount: 42, publishedCardCount: 180, completionRate: 23 }
      )
    }
  };

  const activePlanId = state.books.activePlanId;
  const activePlan = activePlanId ? state.planStates[activePlanId] : undefined;
  if (!activePlan) {
    throw new Error("固定演示数据缺少当前学习计划分区");
  }
  if (scenario === "empty-today") {
    activePlan.today = {
      ...activePlan.today,
      dueCount: 0,
      reviewedCount: 0,
      completionRate: 100,
      tasks: []
    };
  }
  if (scenario === "logged-out") {
    // 首次登录演示种子本身没有当前计划，Plan Gate 才能自然进入首次设置。
    state.books.activePlanId = null;
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
    activePlan.today = {
      ...activePlan.today,
      dueCount: 23,
      masteredCount: 127,
      reviewedCount: 19,
      completionRate: 76
    };
  }
  if (scenario === "mutated") {
    activePlan.media.byCardId[sustainableCard.cardId].stainLevel = 2;
  }

  return state;
}
