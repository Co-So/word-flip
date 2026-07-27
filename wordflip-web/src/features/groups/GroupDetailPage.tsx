import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { StatusTag } from "@/components/StatusTag/StatusTag";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { GroupDetail } from "@/domain/groups";
import styles from "./groups.module.css";

export function GroupDetailPage() {
  const { groupId = "" } = useParams();
  const { groups } = useRepositories();
  const [group, setGroup] = useState<GroupDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setGroup(null);
    setError(null);
    groups.getDetail(groupId)
      .then((detail) => {
        if (active) {
          setGroup(detail);
          setError(null);
        }
      })
      .catch((reason) => {
        if (active) {
          setGroup(null);
          setError((reason as AppError).message ?? "暂时无法获取分组");
        }
      });
    return () => { active = false; };
  }, [groupId, groups]);

  return <AsyncState error={error ?? undefined} status={error ? "error" : group ? "ready" : "loading"}>
    {group ? <div className={styles.page}>
      <Link className={styles.backLink} to="/groups">返回分组</Link>
      <header className={styles.detailHero}>
        <div><p className={styles.eyebrow}>GROUP DETAIL</p><h1>{group.name}</h1><p>{group.cards.length} 张学习卡 · 双轨记忆快照</p></div>
        <StatusTag tone="neutral">只读</StatusTag>
      </header>
      <div className={styles.tableWrap}>
        <table>
          <thead><tr><th>学习卡</th><th>考义</th><th>听写</th><th>选义</th></tr></thead>
          <tbody>{group.cards.map((card) => <tr key={card.cardId}>
            <th scope="row"><strong>{card.headword}</strong><small>{card.cardId}</small></th>
            <td>{card.definition}</td>
            <td><span className={styles.skillLabel}>听写</span><StatusTag tone={card.progress.dictation.heatLevel >= 2 ? "terracotta" : "neutral"}>热力 {card.progress.dictation.heatLevel}</StatusTag></td>
            <td><span className={styles.skillLabel}>选义</span><StatusTag tone={card.progress.choice.heatLevel >= 2 ? "terracotta" : "neutral"}>热力 {card.progress.choice.heatLevel}</StatusTag></td>
          </tr>)}</tbody>
        </table>
      </div>
    </div> : null}
  </AsyncState>;
}
