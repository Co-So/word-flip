import type { ReactNode } from "react";
import { Button } from "@/components/Button/Button";
import styles from "./FocusShell.module.css";

export interface FocusShellProps { title: string; progress: string; aside: ReactNode; onExit: () => void; children: ReactNode; }

export function FocusShell({ title, progress, aside, onExit, children }: FocusShellProps) {
  return <div className={styles.shell}>
    <header className={styles.header}><Button onClick={onExit} variant="ghost">退出</Button><div><p>{progress}</p><h1>{title}</h1></div></header>
    <main className={styles.main}>{children}</main>
    <aside className={styles.aside}>{aside}</aside>
  </div>;
}
