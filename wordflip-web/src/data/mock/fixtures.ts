import type { RepositoryBundle } from "@/data/contracts/RepositoryBundle";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
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
import { MockMediaRepository } from "@/data/mock/repositories/MockMediaRepository";
import { MockStatsRepository } from "@/data/mock/repositories/MockStatsRepository";
import { MockStudyRepository } from "@/data/mock/repositories/MockStudyRepository";
import { MockTodayRepository } from "@/data/mock/repositories/MockTodayRepository";

export { FIXED_ADVANCED_DICTATION_RESULT, FIXED_CHOICE_RESULT, FIXED_DICTATION_RESULT };

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
    media: new MockMediaRepository(store),
    stats: new MockStatsRepository(store)
  };
}
