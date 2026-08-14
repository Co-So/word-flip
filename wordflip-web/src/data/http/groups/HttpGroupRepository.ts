import type { AxiosInstance } from "axios";
import type { AppError } from "@/data/contracts/AppError";
import { mapGroup, mapGroupCardPage, mapGroupList } from "@/data/http/groups/groupMappers";
import type { GroupCardPage, GroupRepository, WordGroup } from "@/domain/groups";

function validation(field: "groupId" | "cardIds", message = "ID 必须是有限正整数"): AppError {
  return {
    kind: "validation",
    message,
    fieldErrors: { [field]: message }
  };
}

function positiveIntegerId(value: string, field: "groupId" | "cardIds"): number {
  if (!/^[1-9]\d*$/.test(value)) throw validation(field);
  const parsed = Number(value);
  // int64 经 JavaScript number 传输时必须保持无损，超出安全整数范围直接拒绝。
  if (!Number.isSafeInteger(parsed)) throw validation(field);
  return parsed;
}

function createCardIds(cardIds: string[]): number[] {
  if (cardIds.length < 1 || cardIds.length > 500) {
    throw validation("cardIds", "请选择 1 到 500 张学习卡");
  }
  const numericIds = cardIds.map((cardId) => positiveIntegerId(cardId, "cardIds"));
  if (new Set(numericIds).size !== numericIds.length) {
    throw validation("cardIds", "学习卡 ID 不得重复");
  }
  return numericIds;
}

/** 使用共享认证客户端访问当前计划分组，失败时不回退 Mock。 */
export class HttpGroupRepository implements GroupRepository {
  constructor(private readonly client: AxiosInstance) {}

  async listGroups(options?: {
    source?: "auto" | "custom";
    sort?: "createdAt" | "name";
  }): Promise<WordGroup[]> {
    const response = await this.client.get<unknown>("/groups", {
      params: options ? { ...options } : undefined
    });
    return mapGroupList(response.data);
  }

  async getDetail(groupId: string): Promise<WordGroup> {
    const id = positiveIntegerId(groupId, "groupId");
    const response = await this.client.get<unknown>(`/groups/${id}`);
    return mapGroup(response.data);
  }

  async listCards(groupId: string, page?: number, size?: number): Promise<GroupCardPage> {
    const id = positiveIntegerId(groupId, "groupId");
    const response = await this.client.get<unknown>(`/groups/${id}/cards`, {
      params: { page, size }
    });
    return mapGroupCardPage(response.data);
  }

  async listUnassigned(options?: {
    all?: boolean;
    q?: string;
    page?: number;
    size?: number;
  }): Promise<GroupCardPage> {
    const response = await this.client.get<unknown>("/learning/cards/unassigned", {
      params: options ? { ...options } : undefined
    });
    return mapGroupCardPage(response.data);
  }

  async createCustomGroup(input: { name?: string; cardIds: string[] }): Promise<WordGroup> {
    const cardIds = createCardIds([...input.cardIds]);
    const payload = input.name === undefined
      ? { cardIds }
      : { name: input.name.trim(), cardIds };
    const response = await this.client.post<unknown>("/groups/custom", payload);
    return mapGroup(response.data);
  }
}
