export interface StatsSummary {
  totalReviewed: number;
  retentionRate: number;
  streakDays: number;
}

export interface StatsRepository {
  getSummary(): Promise<StatsSummary>;
}
