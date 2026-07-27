import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { TodayRepository, TodaySummary } from "@/domain/today";

function noActivePlan() {
  return { kind: "conflict", message: "当前没有可用学习计划" } as const;
}

/** 今日仓储只读取当前计划的固定快照，不推导任务或完成率。 */
export class MockTodayRepository implements TodayRepository {
  constructor(private readonly store: DemoStateStore) {}

  getSummary(): Promise<TodaySummary> {
    const plan = this.store.readActivePlanState();
    return plan ? Promise.resolve(plan.today) : Promise.reject(noActivePlan());
  }
}
