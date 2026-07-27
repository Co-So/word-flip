import type { SkillProgress } from "@/domain/learning";
import type { StatsSummary } from "@/domain/stats";
import type { TodaySummary } from "@/domain/today";

export type QuizSkill = "dictation" | "choice";
export type QueueState = "unlearned" | "fuzzy" | "unknown";
export type QuizSessionStatus = "active" | "completed";
export type QuizScope = "current-plan" | "due-today";

export interface QuizOption {
  key: string;
  label: string;
}

export interface QuizQuestion {
  questionId: string;
  questionIndex: number;
  cardId: string;
  skill: QuizSkill;
  prompt: string;
  hint: string;
  options?: QuizOption[];
}

export interface QuizSession {
  sessionId: string;
  status: QuizSessionStatus;
  skill: QuizSkill;
  scope: QuizScope;
  totalQuestions: number;
  currentIndex: number;
  score: number;
  progressLabel: string;
  question: QuizQuestion;
}

export interface QuizAnswerSubmission {
  sessionId: string;
  requestId: string;
  questionId: string;
  cardId: string;
  answer: string;
}

export interface QuizResult {
  requestId: string;
  accepted: boolean;
  correct: boolean;
  feedback: string;
  expectedAnswer: string | null;
  /** 服务端判题后的预计算快照，由仓储回放到当前计划分区。 */
  precomputed: PrecomputedQuizResult;
}

export interface QuizSkillSummary {
  label: string;
  attempted: number;
  correct: number;
  progressLabel: string;
}

export interface QuizSessionResult {
  sessionId: string;
  status: "completed";
  score: number;
  total: number;
  accuracy: number;
  rating: "excellent" | "good" | "keep_going";
  dictation: QuizSkillSummary;
  choice: QuizSkillSummary;
}

export interface QuizIdempotencyRecord {
  requestId: string;
  userId: string;
  planId: string;
  sessionId: string;
  scope: QuizScope;
  questionId: string;
  cardId: string;
  answer: string;
  response: QuizResult;
}

export interface QuizRepository {
  createSession(skill: QuizSkill, scope: QuizScope): Promise<QuizSession>;
  getSession(sessionId: string): Promise<QuizSession>;
  submitAnswer(submission: QuizAnswerSubmission): Promise<QuizResult>;
  getResult(sessionId: string): Promise<QuizSessionResult>;
}

/** 服务端已经判题并计算完成的演示回放快照。 */
export interface PrecomputedQuizResult {
  cardId: string;
  skill: QuizSkill;
  next: SkillProgress;
  correct: boolean;
  feedback: string;
  expectedAnswer: string | null;
  sessionSnapshot: QuizSession;
  resultSnapshot: QuizSessionResult;
  dashboardSnapshot: TodaySummary;
  statsSnapshot: StatsSummary;
}
