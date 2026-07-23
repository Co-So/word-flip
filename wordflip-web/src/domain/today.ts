export interface TodaySummary {
  dueCount: number;
  masteredCount: number;
  reviewedCount: number;
}

export interface TodayRepository {
  getSummary(): Promise<TodaySummary>;
}
