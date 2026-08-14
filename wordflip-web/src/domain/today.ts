export interface TodayTaskSource {
  groupId: string;
  groupName: string;
  count: number;
}

export interface TodayTask {
  count: number;
  label: string;
  sources: TodayTaskSource[];
}

export interface TodaySummary {
  date: string;
  streakDays: number;
  stats: {
    masteredCount: number;
    dueReviewCount: number;
    completionPercent: number;
  };
  tasks: {
    newWords: TodayTask;
    dueReview: TodayTask;
    quiz: TodayTask;
  };
  recommendedStudy: {
    groupId: string;
    groupName: string;
    wordCount: number;
    reason: "new_words" | "due_review" | "mixed";
  } | null;
  recentGroups: Array<{ groupId: string; name: string; lastStudiedAt: string }>;
}

export interface TodayRepository {
  getSummary(): Promise<TodaySummary>;
}
