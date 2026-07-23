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
  definition: string;
  progress: Record<QuizSkill, SkillProgress>;
}

export interface StudySession {
  sessionId: string;
  status: "active" | "completed";
}

export interface StudyRepository {
  getCards(): Promise<LearningCard[]>;
  getSession(sessionId: string): Promise<StudySession>;
  completeSession(sessionId: string): Promise<StudySession>;
}
