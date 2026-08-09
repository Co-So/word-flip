import type { RepositoryBundle } from "@/data/contracts/RepositoryBundle";
import { HttpAuthRepository } from "@/data/http/auth/HttpAuthRepository";
import { HttpBookRepository } from "@/data/http/books/HttpBookRepository";
import { createHttpRuntime } from "@/data/http/createHttpRuntime";
import { HttpSettingsRepository } from "@/data/http/settings/HttpSettingsRepository";
import { LocalSettingsStore } from "@/data/http/settings/LocalSettingsStore";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import { createMockRepositoryBundle } from "@/data/mock/fixtures";

export interface RepositoryRuntimeOptions {
  dataSource: string | undefined;
  apiBaseUrl: string | undefined;
  storage: Storage | null;
  demoStore: DemoStateStore;
}

/** 按模块组装数据源；WEB-API02 让认证、词书与设置共享同一 HTTP 会话。 */
export function createRepositoryBundle({
  dataSource,
  apiBaseUrl,
  storage,
  demoStore
}: RepositoryRuntimeOptions): RepositoryBundle {
  const repositories = createMockRepositoryBundle(demoStore);
  if (dataSource?.trim().toLowerCase() !== "http") return repositories;

  const normalizedBaseUrl = apiBaseUrl?.trim();
  if (!normalizedBaseUrl) {
    throw new Error("VITE_DATA_SOURCE=http 时必须配置 VITE_API_BASE_URL");
  }
  const runtime = createHttpRuntime({ baseURL: normalizedBaseUrl, storage });
  return {
    ...repositories,
    auth: new HttpAuthRepository(
      runtime.publicClient,
      runtime.authenticatedClient,
      runtime.sessions
    ),
    books: new HttpBookRepository(runtime.authenticatedClient),
    settings: new HttpSettingsRepository(
      runtime.authenticatedClient,
      new LocalSettingsStore(storage)
    )
  };
}
