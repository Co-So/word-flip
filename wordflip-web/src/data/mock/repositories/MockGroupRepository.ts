import type { AppError } from "@/data/contracts/AppError";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { DemoWordGroup } from "@/data/mock/createDemoState";
import type {
  FsrsSkillSnapshot,
  GroupCard,
  GroupCardPage,
  GroupRepository,
  WordGroup
} from "@/domain/groups";
import type { LearningCard, SkillProgress } from "@/domain/learning";

function notFound(message: string): AppError {
  return { kind: "not-found", message };
}

function noActivePlan(): AppError {
  return { kind: "conflict", message: "当前没有可用学习计划" };
}

function validation(message: string): AppError {
  return { kind: "validation", message, fieldErrors: {} };
}

function conflict(message: string): AppError {
  return { kind: "conflict", message };
}

function publicGroup(group: DemoWordGroup): WordGroup {
  const result = structuredClone(group) as Partial<DemoWordGroup>;
  delete result.cardIds;
  return result as WordGroup;
}

function fsrsSnapshot(progress: SkillProgress): FsrsSkillSnapshot {
  const state = {
    unlearned: "new",
    fuzzy: "learning",
    unknown: "relearning"
  } as const;
  // Mock 只回放稳定的只读快照，不把旧展示字段暴露到 Groups 公共契约。
  return {
    state: state[progress.state],
    dueAt: "2026-07-23T00:00:00Z",
    stability: progress.stability,
    difficulty: 5,
    reps: 0,
    lapses: 0
  };
}

function groupCard(card: LearningCard): GroupCard {
  return {
    cardId: card.cardId,
    lexemeId: `lexeme-${card.wordKey}`,
    headword: card.headword,
    phonetic: card.phonetic,
    primaryPos: null,
    primaryDefinition: card.definition,
    displayHeatLevel: card.progress.dictation.heatLevel,
    progress: {
      dictation: fsrsSnapshot(card.progress.dictation),
      choice: fsrsSnapshot(card.progress.choice)
    }
  };
}

function pageOf(cards: GroupCard[], page = 1, size = 20): GroupCardPage {
  if (!Number.isInteger(page) || page < 1 || !Number.isInteger(size) || size < 1 || size > 100) {
    throw validation("分页参数无效");
  }
  const start = (page - 1) * size;
  return {
    page,
    size,
    totalElements: cards.length,
    totalPages: cards.length === 0 ? 0 : Math.ceil(cards.length / size),
    cards: structuredClone(cards.slice(start, start + size))
  };
}

/** 分组仓储按当前计划和 cardId 取卡，不使用 wordKey 作为成员主键。 */
export class MockGroupRepository implements GroupRepository {
  constructor(private readonly store: DemoStateStore) {}

  listGroups(options?: {
    source?: "auto" | "custom";
    sort?: "createdAt" | "name";
  }): Promise<WordGroup[]> {
    const plan = this.store.readActivePlanState();
    if (!plan) return Promise.reject(noActivePlan());
    const groups = plan.groups.items
      .filter((group) => options?.source === undefined || group.source === options.source)
      .sort((left, right) => {
        if (options?.sort === "name") return left.name.localeCompare(right.name, "zh-CN");
        if (options?.sort === "createdAt") {
          return (left.createdAt ?? "").localeCompare(right.createdAt ?? "");
        }
        return 0;
      })
      .map(publicGroup);
    return Promise.resolve(groups);
  }

  getDetail(groupId: string): Promise<WordGroup> {
    const plan = this.store.readActivePlanState();
    if (!plan) return Promise.reject(noActivePlan());
    const group = plan.groups.items.find((item) => item.groupId === groupId);
    return group
      ? Promise.resolve(publicGroup(group))
      : Promise.reject(notFound("找不到指定分组"));
  }

  listCards(groupId: string, page?: number, size?: number): Promise<GroupCardPage> {
    const plan = this.store.readActivePlanState();
    if (!plan) return Promise.reject(noActivePlan());
    const group = plan.groups.items.find((item) => item.groupId === groupId);
    if (!group) return Promise.reject(notFound("找不到指定分组"));
    const cards = group.cardIds.map((cardId) => plan.cards.byCardId[cardId]);
    if (cards.some((card) => card === undefined)) {
      return Promise.reject(notFound("分组包含无效学习卡"));
    }
    return Promise.resolve(pageOf(cards.map((card) => groupCard(card!)), page, size));
  }

  listUnassigned(options?: {
    all?: boolean;
    q?: string;
    page?: number;
    size?: number;
  }): Promise<GroupCardPage> {
    const plan = this.store.readActivePlanState();
    if (!plan) return Promise.reject(noActivePlan());
    const assigned = new Set(plan.groups.items.flatMap((group) => group.cardIds));
    const query = options?.q?.trim().toLocaleLowerCase("en-US") ?? "";
    const cards = Object.values(plan.cards.byCardId)
      .filter((card) => !assigned.has(card.cardId))
      .filter((card) => {
        if (!query) return true;
        return card.headword.toLocaleLowerCase("en-US").startsWith(query) ||
          card.definition.startsWith(query);
      })
      .map(groupCard);
    if (options?.all) {
      return Promise.resolve({
        page: 1,
        size: cards.length,
        totalElements: cards.length,
        totalPages: cards.length === 0 ? 0 : 1,
        cards: structuredClone(cards)
      });
    }
    return Promise.resolve(pageOf(cards, options?.page, options?.size));
  }

  createCustomGroup(input: { name?: string; cardIds: string[] }): Promise<WordGroup> {
    const cardIds = [...input.cardIds];
    if (cardIds.length < 1 || cardIds.length > 500 || new Set(cardIds).size !== cardIds.length) {
      return Promise.reject(validation("请选择 1 到 500 张不重复的学习卡"));
    }
    const before = this.store.readActivePlanState();
    if (!before) return Promise.reject(noActivePlan());
    const assigned = new Set(before.groups.items.flatMap((group) => group.cardIds));
    if (cardIds.some((cardId) => !before.cards.byCardId[cardId] || assigned.has(cardId))) {
      return Promise.reject(conflict("部分学习卡不属于当前未入组候选池"));
    }
    const trimmedName = input.name?.trim();
    if (trimmedName !== undefined && trimmedName.length > 64) {
      return Promise.reject(validation("分组名称不能超过 64 个字符"));
    }
    let nextIndex = 1;
    while (before.groups.items.some((group) => group.groupId === `custom-group-${nextIndex}`)) {
      nextIndex += 1;
    }
    const group: DemoWordGroup = {
      groupId: `custom-group-${nextIndex}`,
      name: trimmedName || `自定义分组 ${nextIndex}`,
      source: "custom",
      status: "not_started",
      createdAt: `${this.store.read().clock.today}T00:00:00Z`,
      stats: { heat0: cardIds.length, heat1: 0, heat2: 0, heat3: 0, heat4: 0, total: cardIds.length },
      progress: 0,
      cardIds
    };
    this.store.updateActivePlan((draft) => {
      // 创建分组只新增成员关系，卡片内容和双轨记忆快照保持原样。
      draft.groups.items.push(structuredClone(group));
    });
    return Promise.resolve(publicGroup(group));
  }
}
