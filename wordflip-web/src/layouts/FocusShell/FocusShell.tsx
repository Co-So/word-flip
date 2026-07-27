import type { ReactNode, Ref } from "react";
import { Button } from "@/components/Button/Button";
import styles from "./FocusShell.module.css";

export interface FocusShellProps { title: string; progress: string; aside: ReactNode; onExit: () => void; exitButtonRef?: Ref<HTMLButtonElement>; children: ReactNode; }

export function FocusShell({ title, progress, aside, onExit, exitButtonRef, children }: FocusShellProps) {
  return <div className={styles.shell}>
    <header className={styles.header}><Button onClick={onExit} ref={exitButtonRef} variant="ghost">退出</Button><div><p>{progress}</p><h1>{title}</h1></div></header>
    <main className={styles.main} data-testid="page-content">{children}</main>
    <aside className={styles.aside}>{aside}</aside>
  </div>;
}
