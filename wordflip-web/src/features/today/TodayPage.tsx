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

  const tasks = summary ? Object.values(summary.tasks) : [];
  const hasTasks = tasks.some((task) => task.count > 0);

  return <AsyncState error={error ?? undefined} status={error ? "error" : summary ? "ready" : "loading"}>
    {summary ? <div className={styles.page}>
      <header className={styles.hero}>
        <div>
          <p className={styles.eyebrow}>TODAY · {summary.date}</p>
          <h1>今天继续前进</h1>
          <p>{summary.recommendedStudy
            ? `推荐学习 · ${summary.recommendedStudy.groupName}`
            : hasTasks ? "今日任务已准备好" : "今日任务已完成"}</p>
        </div>
        <StatusTag tone="sage">TODAY · READY</StatusTag>
      </header>

      <ul aria-label="今日摘要" className={styles.summaryGrid}>
        {[
          ["待复习", summary.stats.dueReviewCount],
          ["已掌握", summary.stats.masteredCount],
          ["测验任务", summary.tasks.quiz.count],
          ["计划完成度", `${summary.stats.completionPercent}%`]
        ].map(([label, value]) => <li key={label}>
          <span>{label}</span>
          <strong>{value}</strong>
        </li>)}
      </ul>

      {!hasTasks ? <EmptyState
        action={<Link className={styles.secondaryLink} to="/books">浏览词书</Link>}
        description="今天没有待完成的复习或学习任务。"
        title="今天的任务已完成"
      /> : <div className={styles.workspace}>
        <Panel className={styles.tasks} title="今日任务">
          <ul className={styles.taskList}>
            {tasks.filter((task) => task.count > 0).map((task) => <li key={task.label}>
              <div><strong>{task.label}</strong><span>{task.count} 张卡片</span></div>
              <StatusTag tone="terracotta">待完成</StatusTag>
            </li>)}
          </ul>
          <Link className={styles.primaryLink} to="/study/study-demo">开始今日学习</Link>
        </Panel>

        <Panel title="最近学习">
          <ul className={styles.recentList}>
            {summary.recentGroups.slice(0, 3).map((group) => <li aria-label={`最近学习 ${group.name}`} key={group.groupId}>
              <div><strong>{group.name}</strong><span>{group.groupId}</span></div>
              <time dateTime={group.lastStudiedAt}>{group.lastStudiedAt}</time>
            </li>)}
          </ul>
        </Panel>
      </div>}
    </div> : null}
  </AsyncState>;
}
