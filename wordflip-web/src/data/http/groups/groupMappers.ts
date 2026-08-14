import type { AppError } from "@/data/contracts/AppError";
import type {
  FsrsSkillSnapshotDto,
  GroupCardDto,
  GroupCardPageDto,
  GroupDetailDto,
  GroupListResponseDto,
  GroupStatsDto
} from "@/data/http/groups/groupDtos";
import type {
  FsrsSkillSnapshot,
  GroupCard,
  GroupCardPage,
  GroupStats,
  WordGroup
} from "@/domain/groups";

type UnknownRecord = Record<string, unknown>;

function invalidPayload(message: string): AppError {
  return { kind: "unknown", message };
}

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function isSafePositiveInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= 0;
}

function isGroupStats(value: unknown): value is GroupStatsDto {
  return (
    isRecord(value) &&
    isNonNegativeInteger(value.heat0) &&
    isNonNegativeInteger(value.heat1) &&
    isNonNegativeInteger(value.heat2) &&
    isNonNegativeInteger(value.heat3) &&
    isNonNegativeInteger(value.heat4) &&
    isNonNegativeInteger(value.total)
  );
}

function isGroup(value: unknown): value is GroupDetailDto {
  return (
    isRecord(value) &&
    isSafePositiveInteger(value.id) &&
    typeof value.name === "string" &&
    (value.source === "auto" || value.source === "custom") &&
    (value.status === "not_started" || value.status === "learning" || value.status === "completed") &&
    (value.createdAt === undefined || value.createdAt === null || typeof value.createdAt === "string") &&
    isGroupStats(value.stats) &&
    isFiniteNumber(value.progress) &&
    value.progress >= 0 &&
    value.progress <= 1
  );
}

function isFsrsSkill(value: unknown): value is FsrsSkillSnapshotDto {
  return (
    isRecord(value) &&
    (value.state === "new" ||
      value.state === "learning" ||
      value.state === "review" ||
      value.state === "relearning") &&
    typeof value.dueAt === "string" &&
    isFiniteNumber(value.stability) &&
    isFiniteNumber(value.difficulty) &&
    isNonNegativeInteger(value.reps) &&
    isNonNegativeInteger(value.lapses)
  );
}

function isSense(value: unknown): boolean {
  return (
    isRecord(value) &&
    (value.pos === undefined || value.pos === null || typeof value.pos === "string") &&
    (value.cn === undefined || value.cn === null || typeof value.cn === "string") &&
    (value.primary === undefined || typeof value.primary === "boolean")
  );
}

function isCard(value: unknown): value is GroupCardDto {
  return (
    isRecord(value) &&
    isSafePositiveInteger(value.cardId) &&
    isSafePositiveInteger(value.lexemeId) &&
    isSafePositiveInteger(value.bookId) &&
    typeof value.wordKey === "string" &&
    typeof value.en === "string" &&
    (value.phonetic === undefined || value.phonetic === null || typeof value.phonetic === "string") &&
    isSafePositiveInteger(value.version) &&
    Array.isArray(value.senses) &&
    value.senses.every(isSense) &&
    isRecord(value.progress) &&
    isFsrsSkill(value.progress.dictation) &&
    isFsrsSkill(value.progress.choice) &&
    isNonNegativeInteger(value.progress.displayHeatLevel) &&
    value.progress.displayHeatLevel <= 4
  );
}

function assertGroup(value: unknown): asserts value is GroupDetailDto {
  if (!isGroup(value)) throw invalidPayload("分组接口返回数据不完整");
}

function assertGroupList(value: unknown): asserts value is GroupListResponseDto {
  if (
    !isRecord(value) ||
    !Array.isArray(value.groups) ||
    !value.groups.every(isGroup)
  ) {
    throw invalidPayload("分组接口返回数据不完整");
  }
}

function assertCardPage(value: unknown): asserts value is GroupCardPageDto {
  if (
    !isRecord(value) ||
    !isSafePositiveInteger(value.page) ||
    !isSafePositiveInteger(value.size) ||
    !isNonNegativeInteger(value.totalElements) ||
    !isNonNegativeInteger(value.totalPages) ||
    !Array.isArray(value.cards) ||
    !value.cards.every(isCard)
  ) {
    throw invalidPayload("分组学习卡接口返回数据不完整");
  }
}

function mapStats(dto: GroupStatsDto): GroupStats {
  return { ...dto };
}

function mapFsrsSkill(dto: FsrsSkillSnapshotDto): FsrsSkillSnapshot {
  return { ...dto };
}

/** 映射前验证完整分组分支，避免把缺失字段静默转成页面零值。 */
export function mapGroup(payload: unknown): WordGroup {
  assertGroup(payload);
  return {
    groupId: String(payload.id),
    name: payload.name,
    source: payload.source,
    status: payload.status,
    createdAt: payload.createdAt ?? null,
    stats: mapStats(payload.stats),
    progress: payload.progress
  };
}

export function mapGroupList(payload: unknown): WordGroup[] {
  assertGroupList(payload);
  return payload.groups.map(mapGroup);
}

/** 只选择服务端标记的主考义；没有标记时才回退第一条词书专属考义。 */
export function mapGroupCard(dto: GroupCardDto): GroupCard {
  const primarySense = dto.senses.find((sense) => sense.primary === true) ?? dto.senses[0];
  if (!primarySense) throw invalidPayload("学习卡缺少考义");
  if (typeof primarySense.cn !== "string") {
    throw invalidPayload("学习卡考义缺少中文释义");
  }
  return {
    cardId: String(dto.cardId),
    lexemeId: String(dto.lexemeId),
    headword: dto.en,
    phonetic: dto.phonetic ?? null,
    primaryPos: primarySense.pos ?? null,
    primaryDefinition: primarySense.cn,
    displayHeatLevel: dto.progress.displayHeatLevel,
    progress: {
      dictation: mapFsrsSkill(dto.progress.dictation),
      choice: mapFsrsSkill(dto.progress.choice)
    }
  };
}

export function mapGroupCardPage(payload: unknown): GroupCardPage {
  assertCardPage(payload);
  return {
    page: payload.page,
    size: payload.size,
    totalElements: payload.totalElements,
    totalPages: payload.totalPages,
    cards: payload.cards.map(mapGroupCard)
  };
}
