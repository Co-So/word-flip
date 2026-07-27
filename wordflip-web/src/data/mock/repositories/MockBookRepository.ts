import type { AppError } from "@/data/contracts/AppError";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import { activatePrecomputedBookPlan } from "@/data/mock/repositories/MockSettingsRepository";
import type { Book, BookOverview, BookRepository, LearningPlan } from "@/domain/books";

function notFound(message: string): AppError {
  return { kind: "not-found", message };
}

/** 词书仓储只暴露计划状态和预计算进度；激活操作始终保留其他计划分区。 */
export class MockBookRepository implements BookRepository {
  constructor(private readonly store: DemoStateStore) {}

  listBooks(): Promise<Book[]> {
    return Promise.resolve(this.store.read().books.items);
  }

  list(): Promise<BookOverview[]> {
    const state = this.store.read();
    return Promise.resolve(state.books.items.map((book) => this.toOverview(book.bookId)));
  }

  getDetail(bookId: string): Promise<BookOverview> {
    try {
      return Promise.resolve(this.toOverview(bookId));
    } catch (error) {
      return Promise.reject(error);
    }
  }

  getActivePlan(): Promise<LearningPlan | null> {
    const state = this.store.read();
    return Promise.resolve(
      state.books.plans.find((plan) => plan.planId === state.books.activePlanId) ?? null
    );
  }

  switchActivePlan(planId: string): Promise<LearningPlan> {
    try {
      return Promise.resolve(this.store.switchActivePlan(planId));
    } catch (error) {
      return Promise.reject(error);
    }
  }

  activateBook(bookId: string): Promise<LearningPlan> {
    const state = this.store.read();
    const historicalPlan = state.books.plans.find((plan) => plan.bookId === bookId);
    try {
      // 历史计划只切指针；首次计划才回放 Task 4 的固定计划快照。
      const plan = historicalPlan
        ? this.store.switchActivePlan(historicalPlan.planId)
        : activatePrecomputedBookPlan(this.store, bookId, state.settings.groupSize ?? 20);
      return Promise.resolve(plan);
    } catch (error) {
      return Promise.reject(error);
    }
  }

  private toOverview(bookId: string): BookOverview {
    const state = this.store.read();
    const book = state.books.items.find((item) => item.bookId === bookId);
    if (!book) {
      throw notFound("找不到指定词书");
    }
    const plan = state.books.plans.find((item) => item.bookId === bookId);
    const current = plan?.planId === state.books.activePlanId;
    return {
      ...book,
      planId: plan?.planId ?? null,
      planStatus: current ? "current" : plan ? "history" : "available",
      // 词书进度只读取当前计划内的预计算快照，历史计划不泄露到当前视图。
      progress: current && plan ? structuredClone(state.planStates[plan.planId].bookProgress) : null
    };
  }
}
