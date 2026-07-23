import { useEffect, useRef } from "react";
import { NavLink } from "react-router-dom";
import styles from "./CommandPalette.module.css";

export interface CommandDestination { label: string; to: string; }
export interface CommandPaletteProps { open: boolean; onClose: () => void; destinations: CommandDestination[]; }

export function CommandPalette({ open, onClose, destinations }: CommandPaletteProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const panelRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!open) return;
    inputRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
        return;
      }
      if (event.key !== "Tab") return;

      const focusableElements = Array.from(
        panelRef.current?.querySelectorAll<HTMLElement>("a[href], button:not([disabled]), input:not([disabled])") ?? []
      );
      const firstElement = focusableElements[0];
      const lastElement = focusableElements.at(-1);
      if (!firstElement || !lastElement) return;
      if (event.shiftKey && document.activeElement === firstElement) {
        event.preventDefault();
        lastElement.focus();
      } else if (!event.shiftKey && document.activeElement === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose, open]);

  if (!open) return null;

  return <div aria-label="搜索单词或功能" aria-modal="true" className={styles.backdrop} onMouseDown={onClose} role="dialog">
    <section className={styles.palette} onMouseDown={(event) => event.stopPropagation()} ref={panelRef}>
      <label className={styles.label} htmlFor="command-search">搜索单词或功能</label>
      <input id="command-search" placeholder="输入页面或单词…" ref={inputRef} />
      <div className={styles.destinations}>
        {destinations.map(({ label, to }) => <NavLink key={to} onClick={onClose} to={to}>{label}</NavLink>)}
      </div>
      <p><kbd>Esc</kbd> 关闭</p>
    </section>
  </div>;
}
