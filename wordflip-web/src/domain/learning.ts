import type { QueueState, QuizSkill } from "@/domain/quiz";

export interface SkillProgress {
  skill: QuizSkill;
  state: QueueState;
  stability: number;
  heatLevel: 0 | 1 | 2 | 3;
  lastQuizSucceeded: boolean;
}

export interface LearningCard {
  cardId: string;
  wordKey: string;
  headword: string;
  phonetic: string;
  definition: string;
  example: string;
  imageDescription: string;
  progress: Record<QuizSkill, SkillProgress>;
}

export interface StudyQueueSummaryItem {
  label: string;
  value: string;
}

export interface StudySession {
  sessionId: string;
  status: "active" | "completed";
  cardIds: string[];
  progressLabel: string;
  queueSummary: StudyQueueSummaryItem[];
}

export interface StudySessionView extends StudySession {
  cards: LearningCard[];
}

export interface StudyRepository {
  getCards(): Promise<LearningCard[]>;
  getSession(sessionId: string): Promise<StudySessionView>;
  completeSession(sessionId: string): Promise<StudySession>;
}
