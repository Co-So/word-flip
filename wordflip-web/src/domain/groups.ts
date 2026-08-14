export interface GroupStats {
  heat0: number;
  heat1: number;
  heat2: number;
  heat3: number;
  heat4: number;
  total: number;
}

export interface WordGroup {
  groupId: string;
  name: string;
  source: "auto" | "custom";
  status: "not_started" | "learning" | "completed";
  createdAt: string | null;
  stats: GroupStats;
  progress: number;
}

export interface FsrsSkillSnapshot {
  state: "new" | "learning" | "review" | "relearning";
  dueAt: string;
  stability: number;
  difficulty: number;
  reps: number;
  lapses: number;
}

export interface GroupCard {
  cardId: string;
  lexemeId: string;
  headword: string;
  phonetic: string | null;
  primaryPos: string | null;
  primaryDefinition: string;
  displayHeatLevel: 0 | 1 | 2 | 3 | 4;
  progress: {
    dictation: FsrsSkillSnapshot;
    choice: FsrsSkillSnapshot;
  };
}

export interface GroupCardPage {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  cards: GroupCard[];
}

export interface GroupRepository {
  listGroups(options?: {
    source?: "auto" | "custom";
    sort?: "createdAt" | "name";
  }): Promise<WordGroup[]>;
  getDetail(groupId: string): Promise<WordGroup>;
  listCards(groupId: string, page?: number, size?: number): Promise<GroupCardPage>;
  listUnassigned(options?: {
    all?: boolean;
    q?: string;
    page?: number;
    size?: number;
  }): Promise<GroupCardPage>;
  createCustomGroup(input: { name?: string; cardIds: string[] }): Promise<WordGroup>;
}
