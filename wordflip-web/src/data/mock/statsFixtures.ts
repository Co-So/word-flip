import type { StatsSummary } from "@/domain/stats";

const FIXED_HEATMAP_DAYS: StatsSummary["heatmapDays"] = [
  { date: "2025-08-01", intensity: 1, count: 3 },
  { date: "2025-09-01", intensity: 2, count: 8 },
  { date: "2025-10-01", intensity: 3, count: 14 },
  { date: "2025-11-01", intensity: 2, count: 9 },
  { date: "2025-12-01", intensity: 4, count: 21 },
  { date: "2026-01-01", intensity: 3, count: 16 },
  { date: "2026-02-01", intensity: 2, count: 11 },
  { date: "2026-03-01", intensity: 4, count: 24 },
  { date: "2026-04-01", intensity: 3, count: 18 },
  { date: "2026-05-01", intensity: 4, count: 27 },
  { date: "2026-06-01", intensity: 3, count: 19 },
  { date: "2026-07-01", intensity: 4, count: 31 }
];

/** 返回服务端已经汇总好的固定统计快照，Web 不从学习记录重新计算。 */
export function createStatsSnapshot(
  totalReviewed: number,
  retentionRate: number,
  streakDays: number,
  masteredCount: number,
  profile: "core" | "advanced" | "empty" | "new" = "core"
): StatsSummary {
  const presentation = {
    core: {
      achievements: [
        { achievementId: "seven-day-streak", title: "稳定节奏", description: "连续学习超过 7 天" },
        { achievementId: "hundred-mastered", title: "百词里程碑", description: "当前计划已掌握超过 100 词" }
      ],
      dictation: { label: "听写进度", value: "68%", detail: "稳定卡片 86 · 待巩固 24" },
      choice: { label: "选择进度", value: "74%", detail: "稳定卡片 93 · 待巩固 17" }
    },
    advanced: {
      achievements: [
        { achievementId: "seven-day-streak", title: "稳定节奏", description: "连续学习超过 7 天" }
      ],
      dictation: { label: "听写进度", value: "42%", detail: "稳定卡片 18 · 待巩固 9" },
      choice: { label: "选择进度", value: "48%", detail: "稳定卡片 21 · 待巩固 7" }
    },
    empty: {
      achievements: [],
      dictation: { label: "听写进度", value: "0%", detail: "完成首次测验后生成进度" },
      choice: { label: "选择进度", value: "0%", detail: "完成首次测验后生成进度" }
    },
    new: {
      achievements: [],
      dictation: { label: "听写进度", value: "5%", detail: "已完成首次固定演示题" },
      choice: { label: "选择进度", value: "0%", detail: "完成首次选择题后生成进度" }
    }
  } as const;
  const selected = presentation[profile];
  return {
    totalReviewed,
    masteredCount,
    retentionRate,
    streakDays,
    heatmapDays: structuredClone(FIXED_HEATMAP_DAYS),
    achievements: selected.achievements.map((achievement) => ({ ...achievement })),
    skillProgress: {
      dictation: structuredClone(selected.dictation),
      choice: structuredClone(selected.choice)
    }
  };
}
