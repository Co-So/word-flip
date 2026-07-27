import type { QuizSkill } from "@/domain/quiz";

export interface HeatmapDay {
  date: string;
  intensity: 0 | 1 | 2 | 3 | 4;
  count: number;
}

export interface Achievement {
  achievementId: string;
  title: string;
  description: string;
}

export interface SkillProgressSummary {
  label: string;
  value: string;
  detail: string;
}

export interface StatsSummary {
  totalReviewed: number;
  masteredCount: number;
  retentionRate: number;
  streakDays: number;
  heatmapDays: HeatmapDay[];
  achievements: Achievement[];
  skillProgress: Record<QuizSkill, SkillProgressSummary>;
}

export interface StatsRepository {
  getSummary(): Promise<StatsSummary>;
}
