import type { LearningCard } from "@/domain/learning";

export interface WordGroup {
  groupId: string;
  name: string;
  cardIds: string[];
}

export interface GroupDetail extends WordGroup {
  cards: LearningCard[];
}

export interface GroupRepository {
  listGroups(): Promise<WordGroup[]>;
  getDetail(groupId: string): Promise<GroupDetail>;
  appendMembers(groupId: string, cardIds: string[]): Promise<WordGroup>;
}
