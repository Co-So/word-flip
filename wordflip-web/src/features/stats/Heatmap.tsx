import type { HeatmapDay } from "@/domain/stats";
import styles from "./stats.module.css";

export function Heatmap({ days }: { days: HeatmapDay[] }) {
  return <div aria-label="过去 12 个月学习热力图" className={styles.heatmap} role="list">
    {days.map((day) => <div
      aria-label={`${day.date}，学习 ${day.count} 次，强度 ${day.intensity}`}
      className={styles.heatCell}
      data-intensity={day.intensity}
      key={day.date}
      role="listitem"
      title={`${day.date} · ${day.count} 次`}
    >
      <time dateTime={day.date}>{day.date.slice(5, 7)}月</time>
      <strong>{day.count}</strong>
    </div>)}
  </div>;
}
