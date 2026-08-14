import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { Button } from "@/components/Button/Button";
import { StatusTag } from "@/components/StatusTag/StatusTag";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { GroupCardPage, WordGroup } from "@/domain/groups";
import styles from "./groups.module.css";

const sourceLabel = { auto: "自动分组", custom: "自定义分组" } as const;
const statusLabel = { not_started: "未开始", learning: "学习中", completed: "已完成" } as const;
const appErrorKinds = new Set<AppError["kind"]>([
  "validation", "unauthorized", "not-found", "conflict", "unavailable", "unknown"
]);

function safeErrorMessage(reason: unknown): string {
  if (
    typeof reason === "object" && reason !== null &&
    "kind" in reason && typeof reason.kind === "string" && appErrorKinds.has(reason.kind as AppError["kind"]) &&
    "message" in reason && typeof reason.message === "string"
  ) {
    return reason.message;
  }
  return "暂时无法获取分组";
}

export function GroupDetailPage() {
  const { groupId = "" } = useParams();
  const { groups } = useRepositories();
  const [group, setGroup] = useState<WordGroup | null>(null);
  const [cards, setCards] = useState<GroupCardPage | null>(null);
  const [pageState, setPageState] = useState({ groupId, page: 1 });
  const [detailError, setDetailError] = useState<string | null>(null);
  const [cardsError, setCardsError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setGroup(null);
    setDetailError(null);
    setPageState({ groupId, page: 1 });
    groups.getDetail(groupId)
      .then((result) => {
        if (active) {
          setGroup(result);
          setDetailError(null);
        }
      })
      .catch((reason: unknown) => {
        if (active) {
          setGroup(null);
          setDetailError(safeErrorMessage(reason));
        }
      });
    return () => { active = false; };
  }, [groupId, groups]);

  const page = pageState.groupId === groupId ? pageState.page : 1;

  useEffect(() => {
    let active = true;
    setCards(null);
    setCardsError(null);
    // 卡片页独立请求，分页只使用服务端返回的边界与双轨快照。
    groups.listCards(groupId, page, 20)
      .then((result) => {
        if (active) {
          setCards(result);
          setCardsError(null);
        }
      })
      .catch((reason: unknown) => {
        if (active) {
          setCards(null);
          setCardsError(safeErrorMessage(reason));
        }
      });
    return () => { active = false; };
  }, [groupId, groups, page]);

  const error = detailError ?? cardsError;
  const status = error ? "error" : group && cards ? "ready" : "loading";
  return <AsyncState error={error ?? undefined} status={status}>
    {group && cards ? <div className={styles.page}>
      <Link className={styles.backLink} to="/groups">返回分组</Link>
      <header className={styles.detailHero}>
        <div>
          <p className={styles.eyebrow}>GROUP DETAIL</p>
          <h1>{group.name}</h1>
          <p>{group.stats.total} 张学习卡 · {sourceLabel[group.source]} · {statusLabel[group.status]} · 已掌握 {Math.round(group.progress * 100)}%</p>
        </div>
        <StatusTag tone="neutral">双轨只读</StatusTag>
      </header>
      <div className={styles.tableWrap}>
        <table>
          <thead><tr><th>学习卡</th><th>词性 / 考义</th><th>默写</th><th>选择</th></tr></thead>
          <tbody>{cards.cards.map((card) => <tr key={card.cardId}>
            <th scope="row"><strong>{card.headword}</strong><small>{card.phonetic ?? card.cardId}</small></th>
            <td>{card.primaryPos && <small className={styles.partOfSpeech}>{card.primaryPos}</small>}{card.primaryDefinition}</td>
            <td><span className={styles.skillLabel}>默写</span><StatusTag tone="neutral">{card.progress.dictation.state} · S {card.progress.dictation.stability}</StatusTag></td>
            <td><span className={styles.skillLabel}>选择</span><StatusTag tone={card.displayHeatLevel >= 2 ? "terracotta" : "neutral"}>{card.progress.choice.state} · S {card.progress.choice.stability} · 热力 {card.displayHeatLevel}</StatusTag></td>
          </tr>)}</tbody>
        </table>
      </div>
      {cards.totalPages > 1 && <nav aria-label="分组卡片分页" className={styles.pagination}>
        <span>第 {cards.page} / {cards.totalPages} 页</span>
        <div>
          {cards.page > 1 && <Button variant="ghost" onClick={() => setPageState({ groupId, page: page - 1 })}>上一页</Button>}
          {cards.page < cards.totalPages && <Button variant="ghost" onClick={() => setPageState({ groupId, page: page + 1 })}>下一页</Button>}
        </div>
      </nav>}
    </div> : null}
  </AsyncState>;
}
