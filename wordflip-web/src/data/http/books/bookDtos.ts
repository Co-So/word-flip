export interface BookProgressDto {
  masteredCount: number;
  assignedCardCount: number;
  completionPercent: number;
}

export interface BookItemDto {
  id: number;
  name: string;
  source: "builtin" | "imported";
  wordCount: number;
  declaredCount?: number | null;
  selected: boolean;
  canDelete: boolean;
  planId: number | null;
  planStatus: "active" | "paused" | "completed" | null;
  progress: BookProgressDto | null;
}

export interface BookListResponseDto {
  books: BookItemDto[];
}

export interface LearningPlanDto {
  planId: number;
  bookId: number;
  bookName: string;
  status: "active" | "paused" | "completed";
  dailyNewCardLimit: number;
  active: boolean;
  createdAt: string;
}
