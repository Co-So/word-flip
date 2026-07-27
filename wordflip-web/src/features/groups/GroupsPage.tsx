import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { Panel } from "@/components/Panel/Panel";
import { StatusTag } from "@/components/StatusTag/StatusTag";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { WordGroup } from "@/domain/groups";
import styles from "./groups.module.css";

export function GroupsPage() {
  const { groups } = useRepositories();
  const [items, setItems] = useState<WordGroup[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    groups.listGroups()
      .then((result) => { if (active) setItems(result); })
      .catch((reason) => { if (active) setError((reason as AppError).message ?? "暂时无法获取分组"); });
    return () => { active = false; };
  }, [groups]);

  return <AsyncState error={error ?? undefined} status={error ? "error" : items ? "ready" : "loading"}>
    {items ? <div className={styles.page}>
      <header className={styles.hero}>
        <div><p className={styles.eyebrow}>ACTIVE PLAN ONLY</p><h1>当前计划分组</h1></div>
        <StatusTag tone="sage">CARD ID BOUND</StatusTag>
      </header>
      <div className={styles.groupGrid}>
        {items.map((group, index) => <article aria-label={group.name} key={group.groupId}>
          <Panel>
            <span className={styles.groupIndex}>{String(index + 1).padStart(2, "0")}</span>
            <h2>{group.name}</h2>
            <p>{group.cardIds.length} 张学习卡</p>
            <Link className={styles.detailLink} to={`/groups/${group.groupId}`}>查看分组</Link>
          </Panel>
        </article>)}
      </div>
    </div> : null}
  </AsyncState>;
}
