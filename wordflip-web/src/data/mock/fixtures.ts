import type { RepositoryBundle } from "@/data/contracts/RepositoryBundle";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { PlanDemoState } from "@/data/mock/createDemoState";
import type { AppError } from "@/data/contracts/AppError";
import { MockAuthRepository } from "@/data/mock/repositories/MockAuthRepository";
import { MockBookRepository } from "@/data/mock/repositories/MockBookRepository";
import { MockGroupRepository } from "@/data/mock/repositories/MockGroupRepository";
import {
  FIXED_ADVANCED_DICTATION_RESULT,
  FIXED_CHOICE_RESULT,
  FIXED_DICTATION_RESULT,
  MockQuizRepository
} from "@/data/mock/repositories/MockQuizRepository";
import { MockSettingsRepository } from "@/data/mock/repositories/MockSettingsRepository";
import { MockStudyRepository } from "@/data/mock/repositories/MockStudyRepository";
import { MockTodayRepository } from "@/data/mock/repositories/MockTodayRepository";

export { FIXED_ADVANCED_DICTATION_RESULT, FIXED_CHOICE_RESULT, FIXED_DICTATION_RESULT };

function notFound(message: string): AppError {
  return { kind: "not-found", message };
}

function noActivePlan(): AppError {
  return { kind: "conflict", message: "当前没有可用学习计划" };
}

function currentPlan(store: DemoStateStore): PlanDemoState | null {
  return store.readActivePlanState();
}

/** 供视觉演示和测试注入的仓储实现；它只回放服务端固定快照，便于替换为 HTTP 仓储。 */
export function createMockRepositoryBundle(store: DemoStateStore): RepositoryBundle {
  return {
    auth: new MockAuthRepository(store),
    settings: new MockSettingsRepository(store),
    books: new MockBookRepository(store),
    groups: new MockGroupRepository(store),
    today: new MockTodayRepository(store),
    study: new MockStudyRepository(store),
    quiz: new MockQuizRepository(store),
    media: {
      getMedia: (cardId) => {
        const plan = currentPlan(store);
        const media = plan?.media.byCardId[cardId];
        return media ? Promise.resolve(media) : Promise.reject(plan ? notFound("找不到卡片媒体") : noActivePlan());
      },
      saveMedia: (media) => {
        const plan = currentPlan(store);
        if (!plan?.cards.byCardId[media.cardId]) {
          return Promise.reject(plan ? notFound("找不到当前计划的学习卡") : noActivePlan());
        }
        store.updateActivePlan((draft) => {
          draft.media.byCardId[media.cardId] = structuredClone(media);
        });
        return Promise.resolve(currentPlan(store)!.media.byCardId[media.cardId]);
      }
    },
    stats: {
      getSummary: () => {
        const plan = currentPlan(store);
        return plan ? Promise.resolve(plan.stats) : Promise.reject(noActivePlan());
      }
    }
  };
}
