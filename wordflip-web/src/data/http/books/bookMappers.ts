import type { Book, BookOverview, LearningPlan } from "@/domain/books";
import type { BookItemDto, LearningPlanDto } from "@/data/http/books/bookDtos";

/** 将服务端数值 ID 转为 Web 领域层稳定使用的字符串 ID。 */
export function mapBook(dto: BookItemDto): Book {
  return {
    bookId: String(dto.id),
    title: dto.name,
    cardCount: dto.wordCount
  };
}

/** 仅按服务端计划关联与 selected 标记归一化页面需要的三态。 */
export function mapBookOverview(dto: BookItemDto): BookOverview {
  return {
    ...mapBook(dto),
    planId: dto.planId === null ? null : String(dto.planId),
    planStatus: dto.selected ? "current" : dto.planId === null ? "available" : "history",
    progress: dto.progress === null ? null : { ...dto.progress }
  };
}

/** 学习计划 DTO 只保留当前页面领域契约所需字段。 */
export function mapLearningPlan(dto: LearningPlanDto): LearningPlan {
  return {
    planId: String(dto.planId),
    bookId: String(dto.bookId),
    title: dto.bookName
  };
}
