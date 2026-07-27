import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { EmptyState } from "@/components/EmptyState/EmptyState";
import { Panel } from "@/components/Panel/Panel";
import { StatusTag } from "@/components/StatusTag/StatusTag";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { TodaySummary } from "@/domain/today";
import styles from "./TodayPage.module.css";

function errorMessage(error: unknown): string {
  return (error as AppError).message ?? "暂时无法获取今日安排";
}

export function TodayPage() {
  const { today } = useRepositories();
  const [summary, setSummary] = useState<TodaySummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    today.getSummary()
      .then((snapshot) => {
        if (active) setSummary(snapshot);
      })
      .catch((reason) => {
        if (active) setError(errorMessage(reason));
      });
    return () => { active = false; };
  }, [today]);

  return <AsyncState error={error ?? undefined} status={error ? "error" : summary ? "ready" : "loading"}>
    {summary ? <div className={styles.page}>
      <header className={styles.hero}>
        <div>
          <p className={styles.eyebrow}>TODAY · 2026.07.23</p>
          <h1>今天继续前进</h1>
          <p>当前计划 · {summary.currentBookTitle}</p>
        </div>
        <StatusTag tone="sage">MOCK DATA · READY</StatusTag>
      </header>

      <ul aria-label="今日摘要" className={styles.summaryGrid}>
        {[
          ["待复习", summary.dueCount],
          ["已掌握", summary.masteredCount],
          ["今日完成", summary.reviewedCount],
          ["计划完成度", `${summary.completionRate}%`]
        ].map(([label, value]) => <li key={label}>
          <span>{label}</span>
          <strong>{value}</strong>
        </li>)}
      </ul>

      {summary.tasks.length === 0 ? <EmptyState
        action={<Link className={styles.secondaryLink} to="/books">浏览词书</Link>}
        description="今天没有待完成的复习或学习任务。"
        title="今天的任务已完成"
      /> : <div className={styles.workspace}>
        <Panel className={styles.tasks} title="今日任务">
          <ul className={styles.taskList}>
            {summary.tasks.map((task) => <li key={task.taskId}>
              <div><strong>{task.title}</strong><span>{task.description}</span></div>
              <StatusTag tone="terracotta">待完成</StatusTag>
            </li>)}
          </ul>
          <Link className={styles.primaryLink} to="/study/demo">开始今日学习</Link>
        </Panel>

        <Panel title="最近学习">
          <ul className={styles.recentList}>
            {summary.recentStudy.slice(0, 3).map((item) => <li aria-label={`最近学习 ${item.headword}`} key={item.cardId}>
              <div><strong>{item.headword}</strong><span>{item.definition}</span></div>
              <time>{item.reviewedAtLabel}</time>
            </li>)}
          </ul>
        </Panel>
      </div>}
    </div> : null}
  </AsyncState>;
}
