import { useEffect, useRef, type RefObject } from "react";
import { Button } from "@/components/Button/Button";
import styles from "./ExitConfirmationDialog.module.css";

interface ExitConfirmationDialogProps {
  description: string;
  onCancel: () => void;
  onConfirm: () => void;
  returnFocusRef: RefObject<HTMLElement>;
  title: string;
}

/** 离开专注流程前确认，避免键盘误触直接丢失当前进度。 */
export function ExitConfirmationDialog({ description, onCancel, onConfirm, returnFocusRef, title }: ExitConfirmationDialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const returnFocusTarget = returnFocusRef.current;
    cancelRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onCancel();
        return;
      }
      if (event.key !== "Tab") return;
      const first = cancelRef.current;
      const last = confirmRef.current;
      if (!first || !last) return;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      returnFocusTarget?.focus();
    };
  }, [onCancel, returnFocusRef]);

  return (
    <div className={styles.backdrop}>
      <section aria-describedby="exit-confirmation-description" aria-labelledby="exit-confirmation-title" aria-modal="true" className={styles.dialog} role="dialog">
        <p className={styles.eyebrow}>LEAVE FOCUS MODE?</p>
        <h2 id="exit-confirmation-title">{title}</h2>
        <p id="exit-confirmation-description">{description}</p>
        <div className={styles.actions}>
          <Button onClick={onCancel} ref={cancelRef} variant="ghost">继续当前任务</Button>
          <Button onClick={onConfirm} ref={confirmRef}>确认退出</Button>
        </div>
      </section>
    </div>
  );
}
