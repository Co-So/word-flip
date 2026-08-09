import type { AxiosInstance } from "axios";
import type { AppError } from "@/data/contracts/AppError";
import type { LearningPlanDto } from "@/data/http/books/bookDtos";
import { mapLearningPlan } from "@/data/http/books/bookMappers";
import type { LocalSettingsStore } from "@/data/http/settings/LocalSettingsStore";
import type { PreferencesPatchDto, UserSettingsDto } from "@/data/http/settings/settingsDtos";
import type { AppSettings, OnboardingInput, SettingsRepository } from "@/domain/settings";
import type { LearningPlan } from "@/domain/books";

function validation(): AppError {
  return {
    kind: "validation",
    message: "ID 必须是有限正整数",
    fieldErrors: { bookId: "必须是有限正整数" }
  };
}

function positiveIntegerBookId(value: string): number {
  if (!/^[1-9]\d*$/.test(value)) throw validation();
  const parsed = Number(value);
  // int64 经 JavaScript number 传输时必须保持无损。
  if (!Number.isSafeInteger(parsed)) throw validation();
  return parsed;
}

function mapSettings(dto: UserSettingsDto, reducedMotion: boolean): AppSettings {
  return {
    soundEnabled: dto.autoSpeak,
    reducedMotion,
    groupSize: dto.groupSize,
    groupStrategy: dto.groupStrategy
  };
}

/** 组合服务端设置与设备本地动效偏好，不实现业务算法或 Mock 降级。 */
export class HttpSettingsRepository implements SettingsRepository {
  constructor(
    private readonly client: AxiosInstance,
    private readonly localStore: LocalSettingsStore
  ) {}

  async getSettings(): Promise<AppSettings> {
    const response = await this.client.get<UserSettingsDto>("/settings");
    return mapSettings(response.data, this.localStore.read());
  }

  async updateSettings(settings: AppSettings): Promise<AppSettings> {
    const body: PreferencesPatchDto = {
      autoSpeak: settings.soundEnabled,
      groupSize: settings.groupSize
    };
    // PATCH 省略字段表示保持服务端原值，不得用默认策略替代。
    if (settings.groupStrategy !== undefined) body.groupStrategy = settings.groupStrategy;
    const response = await this.client.patch<UserSettingsDto>("/settings/preferences", body);
    // 服务端成功后才提交本地偏好，失败时保持原值。
    this.localStore.write(settings.reducedMotion);
    return mapSettings(response.data, settings.reducedMotion);
  }

  async saveOnboarding(input: OnboardingInput): Promise<LearningPlan> {
    const bookId = positiveIntegerBookId(input.bookId);
    const preferences: PreferencesPatchDto = {
      groupSize: input.groupSize,
      groupStrategy: input.groupStrategy
    };
    // 重试时从设置步骤重新开始，依赖服务端保证计划创建幂等。
    await this.client.patch<UserSettingsDto>("/settings/preferences", preferences);
    const response = await this.client.post<LearningPlanDto>("/learning-plans", { bookId });
    return mapLearningPlan(response.data);
  }

  supportsDemoReset(): boolean {
    return false;
  }

  resetDemo(): Promise<AppSettings> {
    return Promise.reject({
      kind: "validation",
      message: "HTTP 数据源不支持重置演示数据",
      fieldErrors: {}
    } satisfies AppError);
  }
}
