import type { AppError } from "@/data/contracts/AppError";
import type {
  TodayDashboardDto,
  TodayRecommendedStudyDto,
  TodayRecentGroupDto,
  TodayTaskDto,
  TodayTaskSourceDto
} from "@/data/http/today/todayDtos";
import type { TodaySummary, TodayTask, TodayTaskSource } from "@/domain/today";

type UnknownRecord = Record<string, unknown>;

function incomplete(): AppError {
  return { kind: "unknown", message: "今日接口返回数据不完整" };
}

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function isTaskSource(value: unknown): value is TodayTaskSourceDto {
  return (
    isRecord(value) &&
    isNumber(value.groupId) &&
    typeof value.groupName === "string" &&
    isNumber(value.count)
  );
}

function isTask(value: unknown): value is TodayTaskDto {
  return (
    isRecord(value) &&
    isNumber(value.count) &&
    typeof value.label === "string" &&
    (value.sources === undefined ||
      (Array.isArray(value.sources) && value.sources.every(isTaskSource)))
  );
}

function isRecommendedStudy(value: unknown): value is TodayRecommendedStudyDto {
  return (
    isRecord(value) &&
    isNumber(value.groupId) &&
    typeof value.groupName === "string" &&
    isNumber(value.wordCount) &&
    (value.reason === "new_words" || value.reason === "due_review" || value.reason === "mixed")
  );
}

function isRecentGroup(value: unknown): value is TodayRecentGroupDto {
  return (
    isRecord(value) &&
    isNumber(value.groupId) &&
    typeof value.name === "string" &&
    typeof value.lastStudiedAt === "string"
  );
}

function assertDashboard(value: unknown): asserts value is TodayDashboardDto {
  if (
    !isRecord(value) ||
    typeof value.date !== "string" ||
    !isNumber(value.streakDays) ||
    !isRecord(value.stats) ||
    !isNumber(value.stats.masteredCount) ||
    !isNumber(value.stats.dueReviewCount) ||
    !isNumber(value.stats.completionPercent) ||
    !isRecord(value.tasks) ||
    !isTask(value.tasks.newWords) ||
    !isTask(value.tasks.dueReview) ||
    !isTask(value.tasks.quiz) ||
    (value.recommendedStudy !== null && !isRecommendedStudy(value.recommendedStudy)) ||
    !Array.isArray(value.recentGroups) ||
    !value.recentGroups.every(isRecentGroup)
  ) {
    throw incomplete();
  }
}

function mapTaskSource(dto: TodayTaskSourceDto): TodayTaskSource {
  return { groupId: String(dto.groupId), groupName: dto.groupName, count: dto.count };
}

function mapTask(dto: TodayTaskDto): TodayTask {
  return {
    count: dto.count,
    label: dto.label,
    sources: (dto.sources ?? []).map(mapTaskSource)
  };
}

/** 校验服务端必填分支后生成独立快照，并统一将分组 ID 转为字符串。 */
export function mapTodaySummary(payload: unknown): TodaySummary {
  assertDashboard(payload);
  return {
    date: payload.date,
    streakDays: payload.streakDays,
    stats: { ...payload.stats },
    tasks: {
      newWords: mapTask(payload.tasks.newWords),
      dueReview: mapTask(payload.tasks.dueReview),
      quiz: mapTask(payload.tasks.quiz)
    },
    recommendedStudy:
      payload.recommendedStudy === null
        ? null
        : {
            ...payload.recommendedStudy,
            groupId: String(payload.recommendedStudy.groupId)
          },
    recentGroups: payload.recentGroups.map((group) => ({
      groupId: String(group.groupId),
      name: group.name,
      lastStudiedAt: group.lastStudiedAt
    }))
  };
}
