import type { ReactNode } from "react";

export interface StatusTagProps { tone?: "neutral" | "sage" | "terracotta" | "error"; children: ReactNode; }

export function StatusTag({ tone = "neutral", children }: StatusTagProps) {
  return <span className={`wf-status-tag wf-status-tag--${tone}`}>{children}</span>;
}
