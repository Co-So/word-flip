import { useCallback, useEffect, useState } from "react";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { Panel } from "@/components/Panel/Panel";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { StatsSummary } from "@/domain/stats";
import { Heatmap } from "./Heatmap";
import styles from "./stats.module.css";

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : "暂时无法获取统计快照";
}

export function StatsPage() {
  const { stats } = useRepositories();
  const [summary, setSummary] = useState<StatsSummary | null>(null);
  const [status, setStatus] = useState<"loading" | "error" | "ready">("loading");
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    setStatus("loading");
    try {
      setSummary(await stats.getSummary());
      setStatus("ready");
    } catch (reason) {
      setError(messageOf(reason));
      setStatus("error");
    }
  }, [stats]);

  useEffect(() => {
    void load();
  }, [load]);

  return <div className={styles.page}>
    <header className={styles.hero}><p className={styles.eyebrow}>PROGRESS · SERVER SNAPSHOT</p>
      <h1>学习统计</h1><p>汇总值来自当前计划的固定服务端快照。</p></header>
    <AsyncState error={error} onRetry={load} status={status}>
      {summary ? <>
        <ul aria-label="学习摘要" className={styles.summaryGrid}>
          <li><span>累计复习</span><strong>{summary.totalReviewed.toLocaleString("zh-CN")}</strong></li>
          <li><span>已掌握</span><strong>{summary.masteredCount}</strong></li>
          <li><span>保持率</span><strong>{(summary.retentionRate * 100).toFixed(1)}%</strong></li>
          <li><span>连续学习</span><strong>{summary.streakDays} 天</strong></li>
        </ul>
        <Panel title="过去 12 个月"><Heatmap days={summary.heatmapDays} /></Panel>
        <div className={styles.lowerGrid}>
          <Panel title="双轨进度">
            <div className={styles.skillGrid}>
              {(["dictation", "choice"] as const).map((skill) => {
                const progress = summary.skillProgress[skill];
                return <article key={skill}><h3>{progress.label}</h3><strong>{progress.value}</strong><p>{progress.detail}</p></article>;
              })}
            </div>
          </Panel>
          <Panel title="成就">
            <ul className={styles.achievements}>{summary.achievements.map((achievement) =>
              <li key={achievement.achievementId}><strong>{achievement.title}</strong><span>{achievement.description}</span></li>
            )}</ul>
          </Panel>
        </div>
      </> : null}
    </AsyncState>
  </div>;
}
