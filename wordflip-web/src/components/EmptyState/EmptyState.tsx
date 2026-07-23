import type { ReactNode } from "react";

export interface EmptyStateProps { title: string; description?: string; action?: ReactNode; }

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return <section className="wf-empty-state"><h2>{title}</h2>{description && <p>{description}</p>}{action}</section>;
}
