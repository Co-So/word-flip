import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { StatusTag } from "@/components/StatusTag/StatusTag";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { GroupCard, WordGroup } from "@/domain/groups";
import styles from "./groups.module.css";

interface GroupDetailView {
  group: WordGroup;
  cards: GroupCard[];
}

export function GroupDetailPage() {
  const { groupId = "" } = useParams();
  const { groups } = useRepositories();
  const [detail, setDetail] = useState<GroupDetailView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setDetail(null);
    setError(null);
    Promise.all([groups.getDetail(groupId), groups.listCards(groupId)])
      .then(([group, page]) => {
        if (active) {
          setDetail({ group, cards: page.cards });
          setError(null);
        }
      })
      .catch((reason) => {
        if (active) {
          setDetail(null);
          setError((reason as AppError).message ?? "暂时无法获取分组");
        }
      });
    return () => { active = false; };
  }, [groupId, groups]);

  const group = detail?.group;
  const cards = detail?.cards;
  return <AsyncState error={error ?? undefined} status={error ? "error" : detail ? "ready" : "loading"}>
    {group && cards ? <div className={styles.page}>
      <Link className={styles.backLink} to="/groups">返回分组</Link>
      <header className={styles.detailHero}>
        <div><p className={styles.eyebrow}>GROUP DETAIL</p><h1>{group.name}</h1><p>{cards.length} 张学习卡 · 双轨记忆快照</p></div>
        <StatusTag tone="neutral">只读</StatusTag>
      </header>
      <div className={styles.tableWrap}>
        <table>
          <thead><tr><th>学习卡</th><th>考义</th><th>听写</th><th>选义</th></tr></thead>
          <tbody>{cards.map((card) => <tr key={card.cardId}>
            <th scope="row"><strong>{card.headword}</strong><small>{card.cardId}</small></th>
            <td>{card.primaryDefinition}</td>
            <td><span className={styles.skillLabel}>听写</span><StatusTag tone="neutral">{card.progress.dictation.state} · S {card.progress.dictation.stability}</StatusTag></td>
            <td><span className={styles.skillLabel}>选义</span><StatusTag tone={card.displayHeatLevel >= 2 ? "terracotta" : "neutral"}>{card.progress.choice.state} · S {card.progress.choice.stability} · 热力 {card.displayHeatLevel}</StatusTag></td>
          </tr>)}</tbody>
        </table>
      </div>
    </div> : null}
  </AsyncState>;
}
