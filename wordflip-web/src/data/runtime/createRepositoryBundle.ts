import type { RepositoryBundle } from "@/data/contracts/RepositoryBundle";
import { createHttpAuthRepository } from "@/data/http/auth/HttpAuthRepository";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import { createMockRepositoryBundle } from "@/data/mock/fixtures";

export interface RepositoryRuntimeOptions {
  dataSource: string | undefined;
  apiBaseUrl: string | undefined;
  storage: Storage | null;
  demoStore: DemoStateStore;
}

/** 按模块组装数据源；WEB-API01 只允许认证切到真实 HTTP。 */
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
  return {
    ...repositories,
    auth: createHttpAuthRepository({ baseURL: normalizedBaseUrl, storage })
  };
}
