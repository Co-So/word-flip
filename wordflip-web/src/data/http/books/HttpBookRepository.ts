import type { AxiosInstance } from "axios";
import type { AppError } from "@/data/contracts/AppError";
import type { BookListResponseDto, BookItemDto, LearningPlanDto } from "@/data/http/books/bookDtos";
import { mapBook, mapBookOverview, mapLearningPlan } from "@/data/http/books/bookMappers";
import type { Book, BookOverview, BookRepository, LearningPlan } from "@/domain/books";

function validation(field: "bookId" | "planId"): AppError {
  return {
    kind: "validation",
    message: "ID 必须是有限正整数",
    fieldErrors: { [field]: "必须是有限正整数" }
  };
}

function positiveIntegerId(value: string, field: "bookId" | "planId"): number {
  if (!/^[1-9]\d*$/.test(value)) {
    throw validation(field);
  }
  const parsed = Number(value);
  // int64 经 JavaScript number 传输时必须保持无损，超出安全整数范围直接拒绝。
  if (!Number.isSafeInteger(parsed)) {
    throw validation(field);
  }
  return parsed;
}

function isNotFound(error: unknown): error is AppError {
  return typeof error === "object" && error !== null && "kind" in error && error.kind === "not-found";
}

/** 使用共享认证客户端访问词书与学习计划端点，不引入任何 Mock 降级。 */
export class HttpBookRepository implements BookRepository {
  constructor(private readonly client: AxiosInstance) {}

  async listBooks(): Promise<Book[]> {
    const response = await this.client.get<BookListResponseDto>("/books");
    return response.data.books.map(mapBook);
  }

  async list(): Promise<BookOverview[]> {
    const response = await this.client.get<BookListResponseDto>("/books");
    return response.data.books.map(mapBookOverview);
  }

  async getDetail(bookId: string): Promise<BookOverview> {
    const id = positiveIntegerId(bookId, "bookId");
    const response = await this.client.get<BookItemDto>(`/books/${id}`);
    return mapBookOverview(response.data);
  }

  async getActivePlan(): Promise<LearningPlan | null> {
    try {
      const response = await this.client.get<LearningPlanDto>("/learning-plans/current");
      return mapLearningPlan(response.data);
    } catch (error) {
      // 当前计划不存在是有效的首次设置状态，不等同于词书详情缺失。
      if (isNotFound(error)) return null;
      throw error;
    }
  }

  async switchActivePlan(planId: string): Promise<LearningPlan> {
    const id = positiveIntegerId(planId, "planId");
    const response = await this.client.patch<LearningPlanDto>(
      "/learning-plans/current",
      { planId: id }
    );
    return mapLearningPlan(response.data);
  }

  async activateBook(bookId: string): Promise<LearningPlan> {
    const id = positiveIntegerId(bookId, "bookId");
    const response = await this.client.post<LearningPlanDto>("/learning-plans", { bookId: id });
    return mapLearningPlan(response.data);
  }
}
