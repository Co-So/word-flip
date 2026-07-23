import { createContext, useContext, type PropsWithChildren } from "react";
import type { RepositoryBundle } from "@/data/contracts/RepositoryBundle";

const RepositoryContext = createContext<RepositoryBundle | null>(null);

export function RepositoryProvider({
  repositories,
  children
}: PropsWithChildren<{ repositories: RepositoryBundle }>) {
  return <RepositoryContext.Provider value={repositories}>{children}</RepositoryContext.Provider>;
}

/** 页面只通过可替换的仓储契约取数，避免直接耦合演示 fixture。 */
// 此 Hook 与 Provider 必须共处同一模块，方便页面使用唯一的依赖注入入口。
// eslint-disable-next-line react-refresh/only-export-components
export function useRepositories(): RepositoryBundle {
  const repositories = useContext(RepositoryContext);
  if (!repositories) {
    throw new Error("useRepositories 必须在 RepositoryProvider 内使用");
  }
  return repositories;
}
