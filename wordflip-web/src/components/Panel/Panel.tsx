import type { ReactNode } from "react";
import styles from "./Panel.module.css";

export interface PanelProps { title?: ReactNode; action?: ReactNode; children: ReactNode; className?: string; }

export function Panel({ title, action, children, className }: PanelProps) {
  return <section className={[styles.panel, className].filter(Boolean).join(" ")}>
    {(title || action) && <header className={styles.header}>{title && <h2>{title}</h2>}{action}</header>}
    {children}
  </section>;
}
