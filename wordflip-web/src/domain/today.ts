export interface RecentStudyItem {
  cardId: string;
  headword: string;
  definition: string;
  reviewedAtLabel: string;
}

export interface TodayTask {
  taskId: string;
  title: string;
  description: string;
}

export interface TodaySummary {
  dueCount: number;
  masteredCount: number;
  reviewedCount: number;
  completionRate: number;
  currentBookTitle: string;
  recentStudy: RecentStudyItem[];
  tasks: TodayTask[];
}

export interface TodayRepository {
  getSummary(): Promise<TodaySummary>;
}
