import { useCallback, useEffect, useRef, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import booksIcon from "@/assets/icons/books.svg";
import groupsIcon from "@/assets/icons/groups.svg";
import settingsIcon from "@/assets/icons/settings.svg";
import statsIcon from "@/assets/icons/stats.svg";
import todayIcon from "@/assets/icons/today.svg";
import { CommandPalette, type CommandDestination } from "@/components/CommandPalette/CommandPalette";
import styles from "./AppShell.module.css";

type NavigationDestination = CommandDestination & { icon: string };

const destinations: NavigationDestination[] = [
  { icon: todayIcon, label: "今日", to: "/today" },
  { icon: booksIcon, label: "词书", to: "/books" },
  { icon: groupsIcon, label: "分组", to: "/groups" },
  { icon: statsIcon, label: "统计", to: "/stats" },
  { icon: settingsIcon, label: "设置", to: "/settings" }
];

export function AppShell() {
  const [commandOpen, setCommandOpen] = useState(false);
  const commandTriggerRef = useRef<HTMLButtonElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const openCommands = useCallback(() => {
    setCommandOpen((wasOpen) => {
      // 仅在首次打开时记录焦点，重复快捷键不能覆盖原始恢复目标。
      if (!wasOpen) {
        previousFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      }
      return true;
    });
  }, []);
  const closeCommands = useCallback(() => {
    setCommandOpen(false);
    // 优先回到打开面板前的实际焦点，失效时再回退到稳定的触发按钮。
    if (previousFocusRef.current?.isConnected) {
      previousFocusRef.current.focus();
    } else {
      commandTriggerRef.current?.focus();
    }
  }, []);

  useEffect(() => {
    // 全局快捷键只负责进入工作台命令入口，不干扰普通键入。
    const handleShortcut = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        openCommands();
      }
    };
    window.addEventListener("keydown", handleShortcut);
    return () => window.removeEventListener("keydown", handleShortcut);
  }, [openCommands]);

  return <div className={styles.shell} data-testid="app-shell">
    <aside className={styles.sidebar}>
      <div className={styles.brand}><span>WordFlip</span><small>STUDY DESK</small></div>
      <nav aria-label="主导航" className={styles.navigation}>
        {destinations.map(({ icon, label, to }) => <NavLink className={({ isActive }) => isActive ? styles.active : undefined} end={to === "/today"} key={to} to={to}>
          <span
            aria-hidden="true"
            className={styles.navIcon}
            style={{ WebkitMaskImage: `url("${icon}")`, maskImage: `url("${icon}")` }}
          />
          <span>{label}</span>
        </NavLink>)}
      </nav>
      <button className={styles.commandTrigger} onClick={openCommands} ref={commandTriggerRef} type="button">
        搜索 <kbd>Ctrl K</kbd>
      </button>
    </aside>
    <main className={styles.content}><div className={styles.contentInner} data-testid="page-content"><Outlet /></div></main>
    <CommandPalette destinations={destinations} onClose={closeCommands} open={commandOpen} />
  </div>;
}
