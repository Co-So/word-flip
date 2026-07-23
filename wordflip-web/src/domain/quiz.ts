import type { SkillProgress } from "@/domain/learning";
import type { StatsSummary } from "@/domain/stats";
import type { TodaySummary } from "@/domain/today";

export type QuizSkill = "dictation" | "choice";
export type QueueState = "unlearned" | "fuzzy" | "unknown";

export interface QuizAnswerSubmission {
  sessionId: string;
  requestId: string;
  cardId: string;
  answer: string;
}

export interface QuizResult {
  requestId: string;
  accepted: boolean;
}

export interface QuizRepository {
  submitAnswer(submission: QuizAnswerSubmission): Promise<QuizResult>;
}

/** 服务端已经判题并计算完成的演示回放快照。 */
export interface PrecomputedQuizResult {
  wordKey: string;
  skill: QuizSkill;
  next: SkillProgress;
  dashboardSnapshot: TodaySummary;
  statsSnapshot: StatsSummary;
}
