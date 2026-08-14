import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { Panel } from "@/components/Panel/Panel";
import { StatusTag } from "@/components/StatusTag/StatusTag";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { WordGroup } from "@/domain/groups";
import styles from "./groups.module.css";

const sourceLabel = { auto: "自动分组", custom: "自定义分组" } as const;
const statusLabel = { not_started: "未开始", learning: "学习中", completed: "已完成" } as const;
const appErrorKinds = new Set<AppError["kind"]>([
  "validation",
  "unauthorized",
  "not-found",
  "conflict",
  "unavailable",
  "unknown"
]);

function safeErrorMessage(reason: unknown, fallback: string): string {
  if (
    typeof reason === "object" && reason !== null &&
    "kind" in reason && typeof reason.kind === "string" && appErrorKinds.has(reason.kind as AppError["kind"]) &&
    "message" in reason && typeof reason.message === "string"
  ) {
    return reason.message;
  }
  return fallback;
}

export function GroupsPage() {
  const { groups } = useRepositories();
  const [items, setItems] = useState<WordGroup[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setItems(null);
    setError(null);
    groups.listGroups()
      .then((result) => {
        if (active) {
          setItems(result);
          setError(null);
        }
      })
      .catch((reason: unknown) => {
        if (active) {
          setItems(null);
          // 只展示仓储层标准化的 AppError，避免泄露原始异常细节。
          setError(safeErrorMessage(reason, "暂时无法获取分组"));
        }
      });
    return () => { active = false; };
  }, [groups]);

  const status = error ? "error" : items === null ? "loading" : items.length === 0 ? "empty" : "ready";
  return <AsyncState error={error ?? undefined} status={status}>
    {items && items.length > 0 ? <div className={styles.page}>
      <header className={styles.hero}>
        <div><p className={styles.eyebrow}>ACTIVE PLAN ONLY</p><h1>当前计划分组</h1></div>
        <Link className={styles.createLink} to="/groups/new">新建自定义分组</Link>
      </header>
      <div className={styles.groupGrid}>
        {items.map((group, index) => <article aria-label={group.name} key={group.groupId}>
          <Panel>
            <div className={styles.groupMeta}>
              <span className={styles.groupIndex}>{String(index + 1).padStart(2, "0")}</span>
              <StatusTag tone="neutral">{sourceLabel[group.source]}</StatusTag>
              <StatusTag tone={group.status === "completed" ? "sage" : "neutral"}>{statusLabel[group.status]}</StatusTag>
            </div>
            <h2>{group.name}</h2>
            <p>{group.stats.total} 张学习卡 · <span>已掌握 {Math.round(group.progress * 100)}%</span></p>
            <ul aria-label="热力分布" className={styles.heatList}>
              {[0, 1, 2, 3, 4].map((level) => <li key={level}>
                <span>热力 {level}</span>
                <strong>{group.stats[`heat${level}` as keyof typeof group.stats]}</strong>
              </li>)}
            </ul>
            <Link className={styles.detailLink} to={`/groups/${group.groupId}`}>查看分组</Link>
          </Panel>
        </article>)}
      </div>
    </div> : null}
  </AsyncState>;
}
