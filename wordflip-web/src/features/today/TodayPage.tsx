import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { EmptyState } from "@/components/EmptyState/EmptyState";
import { Panel } from "@/components/Panel/Panel";
import { StatusTag } from "@/components/StatusTag/StatusTag";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { TodaySummary, TodayTask } from "@/domain/today";
import styles from "./TodayPage.module.css";

function errorMessage(error: unknown): string {
  return (error as AppError).message ?? "暂时无法获取今日安排";
}

function formatChineseDate(date: string): string {
  const [year, month, day] = date.split("-").map(Number);
  const weekday = ["日", "一", "二", "三", "四", "五", "六"][new Date(year, month - 1, day).getDay()];
  return `${year}年${month}月${day}日星期${weekday}`;
}

function formatRelativeTime(timestamp: string): string {
  const elapsedMinutes = Math.max(0, Math.floor((Date.now() - new Date(timestamp).getTime()) / 60_000));
  if (elapsedMinutes < 1) return "刚刚";
  if (elapsedMinutes < 60) return `${elapsedMinutes} 分钟前`;
  const elapsedHours = Math.floor(elapsedMinutes / 60);
  if (elapsedHours < 24) return `${elapsedHours} 小时前`;
  return `${Math.floor(elapsedHours / 24)} 天前`;
}

function taskGroupId(task: TodayTask, recommendedStudy: TodaySummary["recommendedStudy"]): string | null {
  // 新词和到期任务优先进入服务端给出的首个来源分组；无来源才使用推荐分组兜底。
  return task.sources[0]?.groupId ?? recommendedStudy?.groupId ?? null;
}

function taskDescription(task: TodayTask, recommendedStudy: TodaySummary["recommendedStudy"]): string {
  const groupName = task.sources[0]?.groupName ?? recommendedStudy?.groupName;
  return groupName ? `${groupName} · ${task.count} 张卡片` : `${task.count} 张卡片`;
}

interface TaskRowProps {
  kind: "newWords" | "dueReview" | "quiz";
  task: TodayTask;
  recommendedStudy: TodaySummary["recommendedStudy"];
}

function TaskRow({ kind, task, recommendedStudy }: TaskRowProps) {
  const groupId = kind === "quiz" ? null : taskGroupId(task, recommendedStudy);
  const content = <div className={styles.taskCopy}>
    <strong>{task.label}</strong>
    <span>{kind === "quiz" ? "测验将在下一阶段开放" : taskDescription(task, recommendedStudy)}</span>
  </div>;

  return <li>
    {groupId ? <Link className={styles.taskLink} to={`/groups/${groupId}`}>{content}</Link> : content}
    <StatusTag tone={kind === "quiz" ? "sage" : "terracotta"}>{task.count}</StatusTag>
  </li>;
}

export function TodayPage() {
  const { today } = useRepositories();
  const [summary, setSummary] = useState<TodaySummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const snapshot = await today.getSummary();
      setSummary(snapshot);
    } catch (reason) {
      setError(errorMessage(reason));
    }
  }, [today]);

  useEffect(() => {
    void load();
  }, [load]);

  const tasks = summary ? [
    ["newWords", summary.tasks.newWords],
    ["dueReview", summary.tasks.dueReview],
    ["quiz", summary.tasks.quiz]
  ] as const : [];
  const hasTasks = tasks.some(([, task]) => task.count > 0);

  return <AsyncState
    error={error ?? undefined}
    onRetry={load}
    status={error && !summary ? "error" : summary ? "ready" : "loading"}
  >
    {summary ? <div className={styles.page}>
      <header className={styles.hero}>
        <div>
          <p className={styles.eyebrow}>{formatChineseDate(summary.date)}</p>
          <h1>今天继续前进</h1>
          <p>{summary.recommendedStudy
            ? `推荐学习 · ${summary.recommendedStudy.groupName}`
            : hasTasks ? "今日任务已准备好" : "今日任务已完成"}</p>
        </div>
        <p className={styles.streak} aria-label={`连续打卡 ${summary.streakDays} 天`}><span aria-hidden="true">🔥</span>连续打卡 {summary.streakDays} 天</p>
      </header>

      <ul aria-label="今日摘要" className={styles.summaryGrid}>
        {[
          ["已掌握", summary.stats.masteredCount],
          ["待复习", summary.stats.dueReviewCount],
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
        <Panel title="最近学习">
          {summary.recentGroups.length === 0 ? <p className={styles.emptyRecent}>暂时没有最近学习的分组。</p> : <ul className={styles.recentList}>
            {summary.recentGroups.slice(0, 3).map((group) => <li key={group.groupId}>
              <Link aria-label={`最近学习 ${group.name}`} className={styles.recentLink} to={`/groups/${group.groupId}`}>
                <strong>{group.name}</strong>
                <span>查看分组详情</span>
              </Link>
              <time dateTime={group.lastStudiedAt}>{formatRelativeTime(group.lastStudiedAt)}</time>
            </li>)}
          </ul>}
        </Panel>

        <Panel className={styles.tasks} title="今日任务">
          <ul className={styles.taskList}>
            {tasks.filter(([, task]) => task.count > 0).map(([kind, task]) => <TaskRow
              key={kind}
              kind={kind}
              recommendedStudy={summary.recommendedStudy}
              task={task}
            />)}
          </ul>
        </Panel>
      </div>}
    </div> : null}
  </AsyncState>;
}
