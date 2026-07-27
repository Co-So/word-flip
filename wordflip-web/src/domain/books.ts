export interface LearningPlan {
  planId: string;
  bookId: string;
  title: string;
}

export interface Book {
  bookId: string;
  title: string;
  cardCount: number;
}

export interface BookProgress {
  learnedCount: number;
  publishedCardCount: number;
  completionRate: number;
}

export interface BookOverview extends Book {
  planId: string | null;
  planStatus: "current" | "history" | "available";
  progress: BookProgress | null;
}

export interface BookRepository {
  listBooks(): Promise<Book[]>;
  list(): Promise<BookOverview[]>;
  getDetail(bookId: string): Promise<BookOverview>;
  getActivePlan(): Promise<LearningPlan | null>;
  switchActivePlan(planId: string): Promise<LearningPlan>;
  activateBook(bookId: string): Promise<LearningPlan>;
}
