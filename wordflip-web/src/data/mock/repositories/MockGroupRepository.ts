import type { AppError } from "@/data/contracts/AppError";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { GroupDetail, GroupRepository, WordGroup } from "@/domain/groups";
import type { LearningCard } from "@/domain/learning";

function notFound(message: string): AppError {
  return { kind: "not-found", message };
}

function noActivePlan(): AppError {
  return { kind: "conflict", message: "当前没有可用学习计划" };
}

/** 分组仓储按当前计划和 cardId 取卡，不使用 wordKey 作为成员主键。 */
export class MockGroupRepository implements GroupRepository {
  constructor(private readonly store: DemoStateStore) {}

  listGroups(): Promise<WordGroup[]> {
    const plan = this.store.readActivePlanState();
    return plan ? Promise.resolve(plan.groups.items) : Promise.reject(noActivePlan());
  }

  getDetail(groupId: string): Promise<GroupDetail> {
    const plan = this.store.readActivePlanState();
    if (!plan) {
      return Promise.reject(noActivePlan());
    }
    const group = plan.groups.items.find((item) => item.groupId === groupId);
    if (!group) {
      return Promise.reject(notFound("找不到指定分组"));
    }
    const cards = group.cardIds.map((cardId) => plan.cards.byCardId[cardId]);
    if (cards.some((card) => !card)) {
      return Promise.reject(notFound("分组包含无效学习卡"));
    }
    return Promise.resolve({
      ...group,
      cards: structuredClone(cards.filter((card): card is LearningCard => card !== undefined))
    });
  }

  appendMembers(groupId: string, cardIds: string[]): Promise<WordGroup> {
    const before = this.store.readActivePlanState();
    const group = before?.groups.items.find((item) => item.groupId === groupId);
    if (!group) {
      return Promise.reject(before ? notFound("找不到指定分组") : noActivePlan());
    }
    this.store.updateActivePlan((draft) => {
      const target = draft.groups.items.find((item) => item.groupId === groupId)!;
      // 分组成员遵循追加语义，不以新数组覆盖现有成员。
      target.cardIds.push(...cardIds.filter((cardId) => !target.cardIds.includes(cardId)));
    });
    return Promise.resolve(
      this.store.readActivePlanState()!.groups.items.find((item) => item.groupId === groupId)!
    );
  }
}
