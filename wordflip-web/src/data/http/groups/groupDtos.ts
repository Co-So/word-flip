export interface GroupStatsDto {
  heat0: number;
  heat1: number;
  heat2: number;
  heat3: number;
  heat4: number;
  total: number;
}

export interface GroupDetailDto {
  id: number;
  name: string;
  source: "auto" | "custom";
  status: "not_started" | "learning" | "completed";
  createdAt?: string | null;
  stats: GroupStatsDto;
  progress: number;
}

export interface GroupListResponseDto {
  groups: GroupDetailDto[];
}

export interface FsrsSkillSnapshotDto {
  state: "new" | "learning" | "review" | "relearning";
  dueAt: string;
  stability: number;
  difficulty: number;
  reps: number;
  lapses: number;
}

export interface GroupCardSenseDto {
  pos?: string | null;
  cn?: string | null;
  primary?: boolean;
}

export interface GroupCardSourceMaterialDto {
  sourceId: string;
  sourceName: string;
  revision: string;
  senses: GroupCardSenseDto[];
}

export interface GroupCardDto {
  cardId: number;
  lexemeId: number;
  bookId: number;
  wordKey: string;
  en: string;
  phonetic?: string | null;
  version: number;
  senses: GroupCardSenseDto[];
  sourceMaterials: GroupCardSourceMaterialDto[];
  progress: {
    dictation: FsrsSkillSnapshotDto;
    choice: FsrsSkillSnapshotDto;
    displayHeatLevel: 0 | 1 | 2 | 3 | 4;
  };
}

export interface GroupCardPageDto {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  cards: GroupCardDto[];
}
