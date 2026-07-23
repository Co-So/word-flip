export interface WordGroup {
  groupId: string;
  name: string;
  cardIds: string[];
}

export interface GroupRepository {
  listGroups(): Promise<WordGroup[]>;
  appendMembers(groupId: string, cardIds: string[]): Promise<WordGroup>;
}
