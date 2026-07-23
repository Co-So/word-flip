import { useCallback, useEffect, useRef, type RefObject } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import styles from "./CommandPalette.module.css";

export interface CommandDestination { label: string; to: string; }
export interface CommandPaletteProps { open: boolean; onClose: () => void; destinations: CommandDestination[]; restoreFocusRef: RefObject<HTMLButtonElement>; }

export function CommandPalette({ open, onClose, destinations, restoreFocusRef }: CommandPaletteProps) {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const close = useCallback(() => {
    onClose();
    restoreFocusRef.current?.focus();
  }, [onClose, restoreFocusRef]);

  useEffect(() => {
    if (!open) return;
    inputRef.current?.focus();
    const closeWithEscape = (event: KeyboardEvent) => { if (event.key === "Escape") close(); };
    window.addEventListener("keydown", closeWithEscape);
    return () => window.removeEventListener("keydown", closeWithEscape);
  }, [close, open]);

  if (!open) return null;

  return <div aria-label="搜索单词或功能" aria-modal="true" className={styles.backdrop} onMouseDown={close} role="dialog">
    <section className={styles.palette} onMouseDown={(event) => event.stopPropagation()}>
      <label className={styles.label} htmlFor="command-search">搜索单词或功能</label>
      <input id="command-search" placeholder="输入页面或单词…" ref={inputRef} />
      <div className={styles.destinations}>
        {destinations.map(({ label, to }) => <NavLink key={to} onClick={() => { navigate(to); close(); }} to={to}>{label}</NavLink>)}
      </div>
      <p><kbd>Esc</kbd> 关闭</p>
    </section>
  </div>;
}
