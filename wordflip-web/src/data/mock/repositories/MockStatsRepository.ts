import type { AppError } from "@/data/contracts/AppError";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { StatsRepository, StatsSummary } from "@/domain/stats";

function noActivePlan(): AppError {
  return { kind: "conflict", message: "当前没有可用学习计划" };
}

/** 统计仓储原样返回当前计划的服务端预计算快照，不读取卡片或答题历史汇总。 */
export class MockStatsRepository implements StatsRepository {
  constructor(private readonly store: DemoStateStore) {}

  getSummary(): Promise<StatsSummary> {
    const plan = this.store.readActivePlanState();
    return plan
      ? Promise.resolve(structuredClone(plan.stats))
      : Promise.reject(noActivePlan());
  }
}
