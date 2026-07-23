import type { LearningCard } from "@/domain/learning";
import type { PrecomputedQuizResult } from "@/domain/quiz";
import { createDemoState, type DemoState } from "@/data/mock/createDemoState";

export const DEMO_STORAGE_KEY = "wordflip.web.demo.v1";

export interface DemoStateStoreOptions {
  initialState?: DemoState;
  storage?: Storage | null;
}

function clone<T>(value: T): T {
  return structuredClone(value);
}

function browserStorage(): Storage | null {
  return typeof window === "undefined" ? null : window.localStorage;
}

function isCompatibleState(value: unknown): value is DemoState {
  return typeof value === "object" && value !== null && "schemaVersion" in value && value.schemaVersion === 1;
}

/** 将 wordKey 查找表重新指向 cardId 主表，避免展示索引成为独立学习真相。 */
function normalizeCardIndexes(state: DemoState): DemoState {
  const byWordKey: Record<string, LearningCard> = {};
  for (const card of Object.values(state.cards.byCardId)) {
    byWordKey[card.wordKey] = card;
  }
  state.cards.byWordKey = byWordKey;
  return state;
}

/**
 * 演示状态只负责持久化与回放服务器预计算快照；不包含判题、FSRS 或统计计算。
 */
export class DemoStateStore {
  private state: DemoState;
  private readonly storage: Storage | null;

  constructor(options: DemoStateStoreOptions = {}) {
    this.storage = options.storage === undefined ? browserStorage() : options.storage;
    this.state = normalizeCardIndexes(clone(options.initialState ?? createDemoState()));
    this.restore();
  }

  read(): DemoState {
    return clone(this.state);
  }

  write(state: DemoState): void {
    this.state = normalizeCardIndexes(clone(state));
    this.storage?.setItem(DEMO_STORAGE_KEY, JSON.stringify(this.state));
  }

  reset(): void {
    this.write(createDemoState());
  }

  update(mutator: (draft: DemoState) => void): void {
    const draft = this.read();
    mutator(draft);
    this.write(draft);
  }

  applyQuizResult(result: PrecomputedQuizResult): void {
    this.update((draft) => {
      const wordKey = result.wordKey.trim().toLowerCase();
      const card = draft.cards.byWordKey[wordKey];
      if (!card) {
        throw new Error(`演示数据中不存在词形：${wordKey}`);
      }

      // 仅复制服务端给出的指定 skill 快照，绝不在 Web 演示层推导记忆或统计。
      draft.cards.byCardId[card.cardId].progress[result.skill] = clone(result.next);
      draft.today = clone(result.dashboardSnapshot);
      draft.stats = clone(result.statsSnapshot);
    });
  }

  private restore(): void {
    const serialized = this.storage?.getItem(DEMO_STORAGE_KEY);
    if (!serialized) {
      return;
    }

    try {
      const parsed: unknown = JSON.parse(serialized);
      if (isCompatibleState(parsed)) {
        this.state = normalizeCardIndexes(parsed);
        return;
      }
    } catch {
      // 损坏或旧版本数据统一恢复为可预测的固定种子。
    }
    this.reset();
  }
}

export function createDemoStateStore(options?: DemoStateStoreOptions): DemoStateStore {
  return new DemoStateStore(options);
}
