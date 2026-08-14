export interface TodayTaskSourceDto {
  groupId: number;
  groupName: string;
  count: number;
}

export interface TodayTaskDto {
  count: number;
  label: string;
  sources?: TodayTaskSourceDto[];
}

export interface TodayRecommendedStudyDto {
  groupId: number;
  groupName: string;
  wordCount: number;
  reason: "new_words" | "due_review" | "mixed";
}

export interface TodayRecentGroupDto {
  groupId: number;
  name: string;
  lastStudiedAt: string;
}

export interface TodayDashboardDto {
  date: string;
  streakDays: number;
  stats: {
    masteredCount: number;
    dueReviewCount: number;
    completionPercent: number;
  };
  tasks: {
    newWords: TodayTaskDto;
    dueReview: TodayTaskDto;
    quiz: TodayTaskDto;
  };
  recommendedStudy: TodayRecommendedStudyDto | null;
  recentGroups: TodayRecentGroupDto[];
}
