import type { LearningPlan } from "@/domain/books";

export interface AppSettings {
  soundEnabled: boolean;
  reducedMotion: boolean;
  groupSize?: 10 | 20 | 30 | 50;
}

export interface OnboardingInput {
  bookId: string;
  groupSize: 10 | 20 | 30 | 50;
  groupStrategy: "book_order";
}

export interface SettingsRepository {
  getSettings(): Promise<AppSettings>;
  updateSettings(settings: AppSettings): Promise<AppSettings>;
  saveOnboarding(input: OnboardingInput): Promise<LearningPlan>;
}
