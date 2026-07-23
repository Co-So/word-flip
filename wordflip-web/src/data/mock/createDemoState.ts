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

export interface DemoState {
  schemaVersion: 1;
  clock: { today: "2026-07-23" };
  auth: { session: AuthSession | null };
  settings: AppSettings;
  books: { activePlan: LearningPlan | null; items: Book[] };
  groups: { items: WordGroup[] };
  cards: { byCardId: Record<string, LearningCard>; byWordKey: Record<string, LearningCard> };
  today: TodaySummary;
  study: { sessions: Record<string, StudySession> };
  quiz: { mode: "dictation" | "choice" | null };
  media: { byCardId: Record<string, CardMedia> };
  stats: StatsSummary;
}

const sustainableCard: LearningCard = {
  cardId: "card-sustainable",
  wordKey: "sustainable",
  headword: "sustainable",
  definition: "可持续的",
  progress: {
    dictation: {
      skill: "dictation",
      state: "fuzzy",
      stability: 12,
      heatLevel: 2,
      lastQuizSucceeded: false
    },
    choice: {
      skill: "choice",
      state: "unlearned",
      stability: 1,
      heatLevel: 0,
      lastQuizSucceeded: false
    }
  }
};

/** 创建可重复的展示数据；数值是固定服务端快照，不代表客户端计算结果。 */
export function createDemoState(scenario: DemoScenario = "configured"): DemoState {
  const card = structuredClone(sustainableCard);
  const state: DemoState = {
    schemaVersion: 1,
    clock: { today: "2026-07-23" },
    auth: {
      session:
        scenario === "logged-out"
          ? null
          : { userId: "demo-user", displayName: "演示用户", authenticated: true }
    },
    settings: { soundEnabled: true, reducedMotion: false },
    books: {
      activePlan: { planId: "plan-core", bookId: "book-core", title: "核心词汇" },
      items: [{ bookId: "book-core", title: "核心词汇", cardCount: 300 }]
    },
    groups: { items: [{ groupId: "group-focus", name: "重点复习", cardIds: [card.cardId] }] },
    cards: { byCardId: { [card.cardId]: card }, byWordKey: { [card.wordKey]: card } },
    today: { dueCount: 24, masteredCount: 126, reviewedCount: 18 },
    study: { sessions: { demo: { sessionId: "demo", status: "active" } } },
    quiz: { mode: null },
    media: { byCardId: { [card.cardId]: { cardId: card.cardId, imageUrl: null, stainLevel: 0 } } },
    stats: { totalReviewed: 842, retentionRate: 0.9, streakDays: 14 }
  };

  if (scenario === "empty-today") {
    state.today = { dueCount: 0, masteredCount: 126, reviewedCount: 0 };
  }
  if (scenario === "empty-books") {
    state.books = { activePlan: null, items: [] };
  }
  if (scenario === "quiz-complete") {
    state.study.sessions.demo.status = "completed";
  }
  if (scenario === "quiz-dictation" || scenario === "quiz-choice") {
    state.quiz.mode = scenario === "quiz-dictation" ? "dictation" : "choice";
  }
  if (scenario === "after-quiz") {
    state.today = { dueCount: 23, masteredCount: 127, reviewedCount: 19 };
  }
  if (scenario === "mutated") {
    state.media.byCardId[card.cardId].stainLevel = 2;
  }

  return state;
}
