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

export interface BookRepository {
  listBooks(): Promise<Book[]>;
  getActivePlan(): Promise<LearningPlan | null>;
  switchActivePlan(planId: string): Promise<LearningPlan>;
}
